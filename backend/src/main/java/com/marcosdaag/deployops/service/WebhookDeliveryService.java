package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.WebhookDeliveryFailureEntity;
import com.marcosdaag.deployops.model.WebhookEndpointEntity;
import com.marcosdaag.deployops.model.enums.WebhookDeliveryStatus;
import com.marcosdaag.deployops.repository.WebhookDeliveryFailureRepository;
import com.marcosdaag.deployops.repository.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Service
public class WebhookDeliveryService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_RESPONSE_BYTES = 4096; // 4 KB limit anti-DoS
    private static final Random RANDOM = new Random();

    private final UrlSecurityValidator urlSecurityValidator;
    private final HmacSignatureService hmacSignatureService;
    private final WebhookPayloadFormatter payloadFormatter;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryFailureRepository failureRepository;
    private final RabbitTemplate rabbitTemplate;
    private final boolean rabbitEnabled;

    @Value("${app.webhook.fast-retry:false}")
    private boolean fastRetryMode;

    @Autowired
    public WebhookDeliveryService(
            UrlSecurityValidator urlSecurityValidator,
            HmacSignatureService hmacSignatureService,
            WebhookPayloadFormatter payloadFormatter,
            WebhookEndpointRepository webhookEndpointRepository,
            WebhookDeliveryFailureRepository failureRepository,
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            @Value("${app.rabbitmq.enabled:false}") boolean rabbitEnabled
    ) {
        this(
                urlSecurityValidator,
                hmacSignatureService,
                payloadFormatter,
                webhookEndpointRepository,
                failureRepository,
                rabbitTemplateProvider != null ? rabbitTemplateProvider.getIfAvailable() : null,
                rabbitEnabled
        );
    }

    public WebhookDeliveryService(
            UrlSecurityValidator urlSecurityValidator,
            HmacSignatureService hmacSignatureService,
            WebhookPayloadFormatter payloadFormatter,
            WebhookEndpointRepository webhookEndpointRepository,
            WebhookDeliveryFailureRepository failureRepository,
            RabbitTemplate rabbitTemplate,
            boolean rabbitEnabled
    ) {
        this.urlSecurityValidator = urlSecurityValidator;
        this.hmacSignatureService = hmacSignatureService;
        this.payloadFormatter = payloadFormatter;
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.failureRepository = failureRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitEnabled = rabbitEnabled && this.rabbitTemplate != null;
    }

    public boolean deliver(OutboxEventEntity event, WebhookEndpointEntity endpoint) {
        if (endpoint == null || !endpoint.isEnabled()) {
            return false;
        }

        String rawBody = payloadFormatter.format(endpoint.getProviderType(), event.getEventType(), event.getPayloadJson());
        int attempt = 0;
        int lastHttpStatus = 0;
        String lastErrorDetail = null;

        while (attempt < MAX_ATTEMPTS) {
            attempt++;
            try {
                DeliveryResult result = executeHttpAttempt(event, endpoint, rawBody);
                if (result.isSuccess()) {
                    endpoint.setLastDeliveryStatus(result.statusCode() + " OK");
                    webhookEndpointRepository.save(endpoint);
                    logger.info("Webhook entregado con exito [Endpoint ID: {}, Evento: {}, Intento: {}/3]",
                            endpoint.getId(), event.getId(), attempt);
                    return true;
                } else {
                    lastHttpStatus = result.statusCode();
                    lastErrorDetail = result.errorDetail();
                }
            } catch (Exception ex) {
                lastHttpStatus = 0;
                lastErrorDetail = ex.getMessage();
            }

            if (attempt < MAX_ATTEMPTS) {
                long backoffMs = calculateBackoffMillis(attempt);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // Agoto los 3 intentos: derivar a Dead Letter Queue y auditar
        handleDeliveryExhausted(event, endpoint, attempt, lastHttpStatus, lastErrorDetail, rawBody);
        return false;
    }

    public boolean retryFailure(Long failureId) {
        WebhookDeliveryFailureEntity failure = failureRepository.findById(failureId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontro la entrega fallida con ID: " + failureId));

        WebhookEndpointEntity endpoint = failure.getWebhook();
        if (endpoint == null) {
            throw new IllegalStateException("El webhook destino asociado ya no existe.");
        }

        String rawBody = failure.getPayloadSnapshot();
        try {
            DeliveryResult result = executeHttpAttempt(failure.getEventId(), failure.getEventType().name(), endpoint, rawBody);
            if (result.isSuccess()) {
                failure.setStatus(WebhookDeliveryStatus.RETRIED_OK);
                failure.setResolvedAt(LocalDateTime.now());
                failure.setHttpStatus(result.statusCode());
                failure.setErrorDetail("Reintento manual exitoso.");
                failureRepository.save(failure);

                endpoint.setLastDeliveryStatus(result.statusCode() + " OK (Reintentado)");
                webhookEndpointRepository.save(endpoint);
                return true;
            } else {
                failure.setHttpStatus(result.statusCode());
                failure.setErrorDetail("Fallo en reintento: " + result.errorDetail());
                failureRepository.save(failure);
                return false;
            }
        } catch (Exception ex) {
            failure.setErrorDetail("Excepcion en reintento: " + ex.getMessage());
            failureRepository.save(failure);
            return false;
        }
    }

    private DeliveryResult executeHttpAttempt(OutboxEventEntity event, WebhookEndpointEntity endpoint, String payload) throws Exception {
        return executeHttpAttempt(event.getId(), event.getEventType().name(), endpoint, payload);
    }

    private DeliveryResult executeHttpAttempt(String eventId, String eventType, WebhookEndpointEntity endpoint, String payload) throws Exception {
        // Mitigacion de DNS Rebinding: Re-validar la URL justo antes de cada conexion
        urlSecurityValidator.validatePublicUrl(endpoint.getTargetUrl());

        long timestampEpochSeconds = Instant.now().getEpochSecond();
        String signature = hmacSignatureService.calculateSignature(endpoint.getSigningSecret(), timestampEpochSeconds, payload);
        String deliveryId = "dlv_" + UUID.randomUUID().toString().substring(0, 18);

        URI uri = URI.create(endpoint.getTargetUrl().trim());
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setInstanceFollowRedirects(false); // Anti-SSRF: prohibir saltos 3xx
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);

        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("User-Agent", "DeployOps-Webhook-Engine/2.0");
        conn.setRequestProperty("X-DeployOps-Event", eventType);
        conn.setRequestProperty("X-DeployOps-Delivery-Id", deliveryId);
        conn.setRequestProperty("X-DeployOps-Timestamp", String.valueOf(timestampEpochSeconds));
        conn.setRequestProperty("X-DeployOps-Signature-256", signature);

        byte[] bodyBytes = payload.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bodyBytes.length);
        conn.getOutputStream().write(bodyBytes);
        conn.getOutputStream().flush();

        int statusCode = conn.getResponseCode();
        boolean is2xx = statusCode >= 200 && statusCode < 300;

        String responsePreview = "";
        try (InputStream is = (is2xx ? conn.getInputStream() : conn.getErrorStream())) {
            if (is != null) {
                byte[] buffer = new byte[MAX_RESPONSE_BYTES];
                int read = is.read(buffer);
                if (read > 0) {
                    responsePreview = new String(buffer, 0, read, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        } finally {
            conn.disconnect();
        }

        if (is2xx) {
            return new DeliveryResult(true, statusCode, null);
        } else {
            return new DeliveryResult(false, statusCode, "HTTP " + statusCode + ": " + truncate(responsePreview, 200));
        }
    }

    private void handleDeliveryExhausted(
            OutboxEventEntity event,
            WebhookEndpointEntity endpoint,
            int attempts,
            int httpStatus,
            String errorDetail,
            String payloadSnapshot
    ) {
        logger.warn("Webhook agoto {} intentos hacia '{}'. Encaminando a Dead Letter Queue y auditando.",
                attempts, endpoint.getTargetUrl());

        endpoint.setLastDeliveryStatus(httpStatus > 0 ? "Fallo HTTP " + httpStatus : "Fallo de red");
        webhookEndpointRepository.save(endpoint);

        if (!failureRepository.existsByEventIdAndWebhook(event.getId(), endpoint)) {
            WebhookDeliveryFailureEntity failure = new WebhookDeliveryFailureEntity();
            failure.setWebhook(endpoint);
            failure.setUser(endpoint.getUser());
            failure.setEventId(event.getId());
            failure.setEventType(event.getEventType());
            failure.setHttpStatus(httpStatus);
            failure.setErrorDetail(truncate(errorDetail != null ? errorDetail : "Reintentos agotados", 990));
            failure.setAttemptCount(attempts);
            failure.setPayloadSnapshot(payloadSnapshot);
            failure.setStatus(WebhookDeliveryStatus.DEAD_LETTER);
            failure.setFailedAt(LocalDateTime.now());
            failureRepository.save(failure);
        }

        if (rabbitEnabled && rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend("deployops.incidents.dlx", "dead.letter", payloadSnapshot);
            } catch (Exception ex) {
                logger.warn("No se pudo publicar a DLQ en RabbitMQ ({}). Registrado en base de datos.", ex.getMessage());
            }
        }
    }

    long calculateBackoffMillis(int attempt) {
        if (fastRetryMode) {
            return 20L * attempt;
        }
        // Produccion: 2s (intento 1), 8s (intento 2), 30s (intento 3) + Jitter +/-20%
        long base = switch (attempt) {
            case 1 -> 2000L;
            case 2 -> 8000L;
            default -> 30000L;
        };
        double jitterMultiplier = 0.8 + (RANDOM.nextDouble() * 0.4); // 0.80 a 1.20
        return (long) (base * jitterMultiplier);
    }

    public void setFastRetryMode(boolean fastRetryMode) {
        this.fastRetryMode = fastRetryMode;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private record DeliveryResult(boolean isSuccess, int statusCode, String errorDetail) {}
}

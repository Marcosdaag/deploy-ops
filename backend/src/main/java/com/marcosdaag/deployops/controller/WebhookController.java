package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.dto.WebhookCreateDTO;
import com.marcosdaag.deployops.dto.WebhookFailureResponseDTO;
import com.marcosdaag.deployops.dto.WebhookResponseDTO;
import com.marcosdaag.deployops.dto.WebhookUpdateDTO;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.WebhookDeliveryFailureEntity;
import com.marcosdaag.deployops.model.WebhookEndpointEntity;
import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.repository.UserRepository;
import com.marcosdaag.deployops.repository.WebhookDeliveryFailureRepository;
import com.marcosdaag.deployops.repository.WebhookEndpointRepository;
import com.marcosdaag.deployops.service.HmacSignatureService;
import com.marcosdaag.deployops.service.UrlSecurityValidator;
import com.marcosdaag.deployops.service.WebhookDeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador REST para la administracion de receptores de Webhooks y gestion de la Dead Letter Queue (DLQ).
 * Provee operaciones para el alta y mantenimiento de canales externos (Slack, Discord, Custom),
 * generacion y rotacion de secretos HMAC-SHA256 protegidos con cifrado en reposo AES-256-GCM,
 * ejecucion de pings de prueba y reintento manual de entregas fallidas.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookEndpointRepository webhookRepository;
    private final WebhookDeliveryFailureRepository failureRepository;
    private final UserRepository userRepository;
    private final HmacSignatureService hmacSignatureService;
    private final UrlSecurityValidator urlSecurityValidator;
    private final WebhookDeliveryService deliveryService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<WebhookResponseDTO>> getWebhooks(Authentication authentication) {
        UserEntity user = getAuthenticatedUser(authentication);
        List<WebhookEndpointEntity> list = (user != null)
                ? webhookRepository.findByUserOrderByIdDesc(user)
                : webhookRepository.findByEnabledTrue();

        List<WebhookResponseDTO> response = list.stream()
                .map(w -> new WebhookResponseDTO(w, null))
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<WebhookResponseDTO> createWebhook(
            @Valid @RequestBody WebhookCreateDTO dto,
            Authentication authentication
    ) {
        UserEntity user = getAuthenticatedUser(authentication);
        String cleanUrl = dto.getTargetUrl().trim();

        // Validacion perimetral Anti-SSRF
        urlSecurityValidator.validatePublicUrl(cleanUrl);

        if (user != null && webhookRepository.existsByTargetUrlAndUser(cleanUrl, user)) {
            throw new IllegalArgumentException("Ya tienes un webhook configurado con esta URL de destino.");
        }

        String rawSecret = hmacSignatureService.generateSecret();
        String secretHint = hmacSignatureService.maskSecret(rawSecret);

        WebhookEndpointEntity entity = new WebhookEndpointEntity();
        entity.setUser(user);
        entity.setName(dto.getName().trim());
        entity.setTargetUrl(cleanUrl);
        entity.setProviderType(dto.getProviderType() != null ? dto.getProviderType() : com.marcosdaag.deployops.model.enums.WebhookProviderType.CUSTOM);
        entity.setSigningSecret(rawSecret);
        entity.setSecretHint(secretHint);
        entity.setEnabled(true);
        entity.setCreatedAt(LocalDateTime.now());

        WebhookEndpointEntity saved = webhookRepository.save(entity);

        // Se retorna el secreto sin mascara una unica vez para que el usuario lo copie
        return ResponseEntity.status(HttpStatus.CREATED).body(new WebhookResponseDTO(saved, rawSecret));
    }

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<WebhookResponseDTO> updateWebhook(
            @PathVariable Long id,
            @RequestBody WebhookUpdateDTO dto,
            Authentication authentication
    ) {
        UserEntity user = getAuthenticatedUser(authentication);
        WebhookEndpointEntity entity = getOwnedWebhook(id, user);

        if (dto.getName() != null && !dto.getName().isBlank()) {
            entity.setName(dto.getName().trim());
        }

        if (dto.getTargetUrl() != null && !dto.getTargetUrl().isBlank()) {
            String cleanUrl = dto.getTargetUrl().trim();
            urlSecurityValidator.validatePublicUrl(cleanUrl);
            entity.setTargetUrl(cleanUrl);
        }

        if (dto.getProviderType() != null) {
            entity.setProviderType(dto.getProviderType());
        }

        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }

        WebhookEndpointEntity saved = webhookRepository.save(entity);
        return ResponseEntity.ok(new WebhookResponseDTO(saved, null));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteWebhook(@PathVariable Long id, Authentication authentication) {
        UserEntity user = getAuthenticatedUser(authentication);
        WebhookEndpointEntity entity = getOwnedWebhook(id, user);

        webhookRepository.delete(entity);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rotate-secret")
    @Transactional
    public ResponseEntity<WebhookResponseDTO> rotateSecret(@PathVariable Long id, Authentication authentication) {
        UserEntity user = getAuthenticatedUser(authentication);
        WebhookEndpointEntity entity = getOwnedWebhook(id, user);

        String newRawSecret = hmacSignatureService.generateSecret();
        entity.setSigningSecret(newRawSecret);
        entity.setSecretHint(hmacSignatureService.maskSecret(newRawSecret));

        WebhookEndpointEntity saved = webhookRepository.save(entity);
        return ResponseEntity.ok(new WebhookResponseDTO(saved, newRawSecret));
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testWebhook(@PathVariable Long id, Authentication authentication) {
        UserEntity user = getAuthenticatedUser(authentication);
        WebhookEndpointEntity entity = getOwnedWebhook(id, user);

        OutboxEventEntity testEvent = new OutboxEventEntity();
        testEvent.setId(UUID.randomUUID().toString());
        testEvent.setEventType(OutboxEventType.WEBHOOK_TEST_PING);
        testEvent.setPayloadJson("{\"eventId\":\"" + testEvent.getId() + "\",\"type\":\"WEBHOOK_TEST_PING\",\"message\":\"Ping de prueba emitido desde consola SRE\"}");
        testEvent.setUserId(user != null ? user.getId() : null);

        boolean success = deliveryService.deliver(testEvent, entity);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "eventId", testEvent.getId(),
                "lastDeliveryStatus", entity.getLastDeliveryStatus() != null ? entity.getLastDeliveryStatus() : "Desconocido"
        ));
    }

    @GetMapping("/failures")
    @Transactional(readOnly = true)
    public ResponseEntity<List<WebhookFailureResponseDTO>> getFailures(Authentication authentication) {
        UserEntity user = getAuthenticatedUser(authentication);
        List<WebhookDeliveryFailureEntity> failures = (user != null)
                ? failureRepository.findByUserOrderByFailedAtDesc(user)
                : failureRepository.findAll();

        List<WebhookFailureResponseDTO> dtos = failures.stream()
                .map(WebhookFailureResponseDTO::new)
                .toList();

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/failures/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryFailure(@PathVariable Long id, Authentication authentication) {
        UserEntity user = getAuthenticatedUser(authentication);
        WebhookDeliveryFailureEntity failure = failureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el fallo de webhook con ID: " + id));

        if (user != null && failure.getUser() != null && !failure.getUser().getId().equals(user.getId())) {
            throw new SecurityException("No tienes permisos para reintentar este mensaje.");
        }

        boolean success = deliveryService.retryFailure(id);
        return ResponseEntity.ok(Map.of(
                "success", success,
                "failureId", id,
                "status", success ? "RETRIED_OK" : "RETRY_FAILED"
        ));
    }

    private UserEntity getAuthenticatedUser(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return userRepository.findByEmail(authentication.getName().toLowerCase()).orElse(null);
        }
        return null;
    }

    private WebhookEndpointEntity getOwnedWebhook(Long id, UserEntity user) {
        WebhookEndpointEntity entity = webhookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el webhook con ID: " + id));

        if (user != null && entity.getUser() != null && !entity.getUser().getId().equals(user.getId())) {
            throw new SecurityException("No tienes permisos para operar sobre este webhook.");
        }
        return entity;
    }
}

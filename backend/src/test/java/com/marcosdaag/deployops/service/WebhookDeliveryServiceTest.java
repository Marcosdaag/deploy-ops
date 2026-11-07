package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.WebhookDeliveryFailureEntity;
import com.marcosdaag.deployops.model.WebhookEndpointEntity;
import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.model.enums.WebhookDeliveryStatus;
import com.marcosdaag.deployops.model.enums.WebhookProviderType;
import com.marcosdaag.deployops.repository.WebhookDeliveryFailureRepository;
import com.marcosdaag.deployops.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookDeliveryServiceTest {

    @Mock
    private UrlSecurityValidator urlSecurityValidator;

    @Mock
    private HmacSignatureService hmacSignatureService;

    @Mock
    private WebhookPayloadFormatter payloadFormatter;

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private WebhookDeliveryFailureRepository failureRepository;

    private WebhookDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        deliveryService = new WebhookDeliveryService(
                urlSecurityValidator,
                hmacSignatureService,
                payloadFormatter,
                webhookEndpointRepository,
                failureRepository,
                (org.springframework.amqp.rabbit.core.RabbitTemplate) null,
                false
        );
    }

    @Test
    @DisplayName("Calcula backoff exponencial con jitter dentro de rangos esperados")
    void shouldCalculateBackoffWithinExpectedBounds() {
        // Intento 1: base 2000ms * (0.80 a 1.20) -> 1600ms a 2400ms
        long b1 = deliveryService.calculateBackoffMillis(1);
        assertTrue(b1 >= 1600 && b1 <= 2450, "Intento 1 fuera de rango: " + b1);

        // Intento 2: base 8000ms * (0.80 a 1.20) -> 6400ms a 9600ms
        long b2 = deliveryService.calculateBackoffMillis(2);
        assertTrue(b2 >= 6400 && b2 <= 9650, "Intento 2 fuera de rango: " + b2);

        // Intento 3: base 30000ms * (0.80 a 1.20) -> 24000ms a 36000ms
        long b3 = deliveryService.calculateBackoffMillis(3);
        assertTrue(b3 >= 24000 && b3 <= 36500, "Intento 3 fuera de rango: " + b3);
    }

    @Test
    @DisplayName("Modo de reintento rapido reduce los tiempos para tests")
    void shouldCalculateFastBackoffInFastMode() {
        deliveryService.setFastRetryMode(true);
        assertEquals(20L, deliveryService.calculateBackoffMillis(1));
        assertEquals(40L, deliveryService.calculateBackoffMillis(2));
    }

    @Test
    @DisplayName("Si el endpoint esta deshabilitado retorna false de inmediato sin peticiones")
    void shouldSkipDisabledWebhook() {
        WebhookEndpointEntity endpoint = new WebhookEndpointEntity();
        endpoint.setEnabled(false);

        OutboxEventEntity event = new OutboxEventEntity();
        event.setId("evt-test");

        boolean result = deliveryService.deliver(event, endpoint);
        assertFalse(result);
        verifyNoInteractions(urlSecurityValidator);
    }

    @Test
    @DisplayName("retryFailure lanza excepcion si el fallo no existe")
    void shouldThrowWhenFailureNotFound() {
        when(failureRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> deliveryService.retryFailure(999L));
    }
}

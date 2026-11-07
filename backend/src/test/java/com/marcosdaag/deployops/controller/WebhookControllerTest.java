package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.dto.WebhookCreateDTO;
import com.marcosdaag.deployops.dto.WebhookResponseDTO;
import com.marcosdaag.deployops.dto.WebhookUpdateDTO;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.WebhookDeliveryFailureEntity;
import com.marcosdaag.deployops.model.WebhookEndpointEntity;
import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.model.enums.WebhookDeliveryStatus;
import com.marcosdaag.deployops.model.enums.WebhookProviderType;
import com.marcosdaag.deployops.repository.UserRepository;
import com.marcosdaag.deployops.repository.WebhookDeliveryFailureRepository;
import com.marcosdaag.deployops.repository.WebhookEndpointRepository;
import com.marcosdaag.deployops.service.HmacSignatureService;
import com.marcosdaag.deployops.service.UrlSecurityValidator;
import com.marcosdaag.deployops.service.WebhookDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private WebhookEndpointRepository webhookRepository;

    @Mock
    private WebhookDeliveryFailureRepository failureRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HmacSignatureService hmacSignatureService;

    @Mock
    private UrlSecurityValidator urlSecurityValidator;

    @Mock
    private WebhookDeliveryService deliveryService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private WebhookController webhookController;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("sre@empresa.com");
    }

    @Test
    @DisplayName("Crea webhook correctamente y retorna rawSecretOnce para copia unica")
    void shouldCreateWebhookAndReturnRawSecretOnce() {
        WebhookCreateDTO dto = new WebhookCreateDTO();
        dto.setName("Discord Alertas");
        dto.setTargetUrl("https://discord.com/api/webhooks/123/xyz");
        dto.setProviderType(WebhookProviderType.DISCORD);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("sre@empresa.com");
        when(userRepository.findByEmail("sre@empresa.com")).thenReturn(Optional.of(user));
        when(webhookRepository.existsByTargetUrlAndUser(dto.getTargetUrl(), user)).thenReturn(false);

        String generatedSecret = "dops_sec_abcdef1234567890abcdef1234567890";
        when(hmacSignatureService.generateSecret()).thenReturn(generatedSecret);
        when(hmacSignatureService.maskSecret(generatedSecret)).thenReturn("dops_sec_••••••••7890");

        when(webhookRepository.save(any(WebhookEndpointEntity.class))).thenAnswer(inv -> {
            WebhookEndpointEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });

        ResponseEntity<WebhookResponseDTO> response = webhookController.createWebhook(dto, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Discord Alertas", response.getBody().getName());
        assertEquals(generatedSecret, response.getBody().getRawSecretOnce(), "Debe retornar el secreto plano solo en la creacion");
        assertEquals("dops_sec_••••••••7890", response.getBody().getSecretHint());

        verify(urlSecurityValidator).validatePublicUrl(dto.getTargetUrl());
    }

    @Test
    @DisplayName("Lista webhooks del usuario autenticado sin revelar secreto plano")
    void shouldListWebhooksWithoutRawSecret() {
        WebhookEndpointEntity w = new WebhookEndpointEntity();
        w.setId(10L);
        w.setName("Slack Alertas");
        w.setTargetUrl("https://hooks.slack.com/services/123");
        w.setSecretHint("dops_sec_••••••••1234");
        w.setEnabled(true);

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("sre@empresa.com");
        when(userRepository.findByEmail("sre@empresa.com")).thenReturn(Optional.of(user));
        when(webhookRepository.findByUserOrderByIdDesc(user)).thenReturn(List.of(w));

        ResponseEntity<List<WebhookResponseDTO>> response = webhookController.getWebhooks(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertNull(response.getBody().get(0).getRawSecretOnce(), "El listado NUNCA debe incluir el secreto plano");
    }

    @Test
    @DisplayName("Rotar secreto genera un nuevo secreto plano y actualiza el hint")
    void shouldRotateSecretSuccessfully() {
        WebhookEndpointEntity w = new WebhookEndpointEntity();
        w.setId(10L);
        w.setUser(user);
        w.setName("Slack Alertas");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("sre@empresa.com");
        when(userRepository.findByEmail("sre@empresa.com")).thenReturn(Optional.of(user));
        when(webhookRepository.findById(10L)).thenReturn(Optional.of(w));

        String newSecret = "dops_sec_new_rotated_secret_9999";
        when(hmacSignatureService.generateSecret()).thenReturn(newSecret);
        when(hmacSignatureService.maskSecret(newSecret)).thenReturn("dops_sec_••••••••9999");
        when(webhookRepository.save(any(WebhookEndpointEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<WebhookResponseDTO> response = webhookController.rotateSecret(10L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(newSecret, response.getBody().getRawSecretOnce());
    }

    @Test
    @DisplayName("Reintento manual de entrega fallida invoca el servicio de entrega")
    void shouldRetryDeliveryFailure() {
        WebhookDeliveryFailureEntity failure = new WebhookDeliveryFailureEntity();
        failure.setId(5L);
        failure.setUser(user);
        failure.setEventId("evt-5");

        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("sre@empresa.com");
        when(userRepository.findByEmail("sre@empresa.com")).thenReturn(Optional.of(user));
        when(failureRepository.findById(5L)).thenReturn(Optional.of(failure));
        when(deliveryService.retryFailure(5L)).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = webhookController.retryFailure(5L, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals("RETRIED_OK", response.getBody().get("status"));
    }
}

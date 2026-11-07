package com.marcosdaag.deployops.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class HmacSignatureServiceTest {

    private HmacSignatureService hmacSignatureService;

    @BeforeEach
    void setUp() {
        hmacSignatureService = new HmacSignatureService();
    }

    @Test
    @DisplayName("Genera secretos con prefijo dops_sec_ y longitud adecuada")
    void shouldGenerateValidSecrets() {
        String secret = hmacSignatureService.generateSecret();
        assertNotNull(secret);
        assertTrue(secret.startsWith("dops_sec_"));
        assertTrue(secret.length() >= 30);
    }

    @Test
    @DisplayName("Calcula firma HMAC-SHA256 con formato sha256=<hex>")
    void shouldCalculateHmacSignatureCorrectly() {
        String secret = "dops_sec_test_secret_1234567890";
        long timestamp = 1762528800L;
        String payload = "{\"test\":true}";

        String signature = hmacSignatureService.calculateSignature(secret, timestamp, payload);

        assertNotNull(signature);
        assertTrue(signature.startsWith("sha256="));
        assertEquals(7 + 64, signature.length()); // "sha256=" (7) + 64 hex characters
    }

    @Test
    @DisplayName("Verifica firma exitosamente dentro de la ventana de tolerancia")
    void shouldVerifySignatureSuccessfullyWithinTolerance() {
        String secret = "dops_sec_test_secret_1234567890";
        long timestamp = Instant.now().getEpochSecond();
        String payload = "{\"event\":\"INCIDENT_OPENED\"}";

        String signature = hmacSignatureService.calculateSignature(secret, timestamp, payload);
        boolean isValid = hmacSignatureService.verifySignature(secret, timestamp, payload, signature, 300);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("Rechaza firma si el timestamp supera la ventana de tolerancia (Anti-Replay)")
    void shouldRejectSignatureWhenTimestampExceedsTolerance() {
        String secret = "dops_sec_test_secret_1234567890";
        long oldTimestamp = Instant.now().getEpochSecond() - 360; // 6 minutos atras (> 300s)
        String payload = "{\"event\":\"INCIDENT_OPENED\"}";

        String signature = hmacSignatureService.calculateSignature(secret, oldTimestamp, payload);
        boolean isValid = hmacSignatureService.verifySignature(secret, oldTimestamp, payload, signature, 300);

        assertFalse(isValid, "Debe rechazar firmas cuyo timestamp difiera mas de 300 segundos");
    }

    @Test
    @DisplayName("Rechaza firma si el payload o el secreto fueron alterados")
    void shouldRejectSignatureWhenPayloadOrSecretAltered() {
        String secret = "dops_sec_test_secret_1234567890";
        long timestamp = Instant.now().getEpochSecond();
        String payload = "{\"event\":\"INCIDENT_OPENED\"}";

        String signature = hmacSignatureService.calculateSignature(secret, timestamp, payload);

        assertFalse(hmacSignatureService.verifySignature(secret, timestamp, "{\"tampered\":true}", signature, 300));
        assertFalse(hmacSignatureService.verifySignature("dops_sec_wrong_secret", timestamp, payload, signature, 300));
    }

    @Test
    @DisplayName("Enmascara el secreto preservando prefijo y ultimos 4 caracteres")
    void shouldMaskSecretCorrectly() {
        String secret = "dops_sec_9b1deb4d3b7d4bad9bdd2b0d7b3dcb6d";
        String masked = hmacSignatureService.maskSecret(secret);

        assertTrue(masked.startsWith("dops_sec_••••••••"));
        assertTrue(masked.endsWith("cb6d"));
        assertFalse(masked.contains("9b1deb4d"));
    }
}

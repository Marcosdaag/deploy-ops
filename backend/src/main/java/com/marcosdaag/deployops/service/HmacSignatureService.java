package com.marcosdaag.deployops.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class HmacSignatureService {

    private static final Logger logger = LoggerFactory.getLogger(HmacSignatureService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String SECRET_PREFIX = "dops_sec_";

    public String generateSecret() {
        byte[] randomBytes = new byte[24];
        SECURE_RANDOM.nextBytes(randomBytes);
        return SECRET_PREFIX + HexFormat.of().formatHex(randomBytes);
    }

    public String calculateSignature(String secret, long timestampEpochSeconds, String rawPayload) {
        if (secret == null || rawPayload == null) {
            throw new IllegalArgumentException("El secreto y la carga util no pueden ser nulos.");
        }

        String canonicalData = timestampEpochSeconds + "." + rawPayload;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(canonicalData.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hmacBytes);
        } catch (Exception ex) {
            logger.error("Error al calcular firma HMAC-SHA256: {}", ex.getMessage());
            throw new IllegalStateException("Error al generar firma criptografica del webhook.", ex);
        }
    }

    public boolean verifySignature(
            String secret,
            long timestampEpochSeconds,
            String rawPayload,
            String headerSignature,
            long toleranceSeconds
    ) {
        if (secret == null || rawPayload == null || headerSignature == null) {
            return false;
        }

        long nowEpoch = Instant.now().getEpochSecond();
        if (Math.abs(nowEpoch - timestampEpochSeconds) > toleranceSeconds) {
            logger.warn("Firma de webhook rechazada: desvio temporal excesivo ({}s vs max {}s).",
                    Math.abs(nowEpoch - timestampEpochSeconds), toleranceSeconds);
            return false;
        }

        String expectedSignature = calculateSignature(secret, timestampEpochSeconds, rawPayload);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] receivedBytes = headerSignature.getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    public String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "dops_sec_••••";
        }
        String clean = secret.trim();
        if (clean.length() <= 10) {
            return "dops_sec_••••";
        }
        String suffix = clean.substring(clean.length() - 4);
        return "dops_sec_••••••••" + suffix;
    }
}

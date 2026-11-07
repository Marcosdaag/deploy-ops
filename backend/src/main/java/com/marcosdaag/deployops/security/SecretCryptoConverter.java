package com.marcosdaag.deployops.security;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
@Component
public class SecretCryptoConverter implements AttributeConverter<String, String> {

    private static final Logger logger = LoggerFactory.getLogger(SecretCryptoConverter.class);

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12; // 96 bits
    private static final int GCM_TAG_LENGTH_BITS = 128; // 16 bytes

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static volatile byte[] sharedKeyBytes;

    @Value("${app.webhook.encryption-key:deployops-default-aes256-encryption-key-32b}")
    private String configuredKey;

    public SecretCryptoConverter() {
    }

    public SecretCryptoConverter(String keyString) {
        initKey(keyString);
    }

    @PostConstruct
    public void init() {
        if (configuredKey != null && !configuredKey.isBlank()) {
            initKey(configuredKey);
        }
    }

    public static void setStaticKey(String keyString) {
        initKey(keyString);
    }

    private static synchronized void initKey(String keyString) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sharedKeyBytes = sha256.digest(keyString.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo inicializar la clave AES-256: " + e.getMessage(), e);
        }
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        return encrypt(plainText);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return dbData;
        }
        return decrypt(dbData);
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        ensureKeyInitialized();

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            SecretKey secretKey = new SecretKeySpec(sharedKeyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception ex) {
            logger.error("Error al cifrar secreto con AES-256-GCM: {}", ex.getMessage());
            throw new IllegalStateException("Error de cifrado criptografico de seguridad.", ex);
        }
    }

    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null) {
            return null;
        }
        ensureKeyInitialized();

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedBase64);
            if (decoded.length <= GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Carga cifrada invalida o truncada.");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            SecretKey secretKey = new SecretKeySpec(sharedKeyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            logger.error("Error al descifrar secreto con AES-256-GCM: {}", ex.getMessage());
            throw new IllegalStateException("Error al descifrar secreto criptografico.", ex);
        }
    }

    private void ensureKeyInitialized() {
        if (sharedKeyBytes == null) {
            String fallback = (configuredKey != null && !configuredKey.isBlank())
                    ? configuredKey
                    : "deployops-default-aes256-encryption-key-32b";
            initKey(fallback);
        }
    }
}

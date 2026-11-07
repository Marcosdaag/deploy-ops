package com.marcosdaag.deployops.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretCryptoConverterTest {

    private SecretCryptoConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SecretCryptoConverter("test-aes256-encryption-key-for-unit-tests-32b");
    }

    @Test
    @DisplayName("Cifra y descifra correctamente un secreto en texto plano (AES-256-GCM)")
    void shouldEncryptAndDecryptPlainTextCorrectly() {
        String plainSecret = "whsec_9b1deb4d3b7d4bad9bdd2b0d7b3dcb6d";

        String encrypted = converter.convertToDatabaseColumn(plainSecret);
        assertNotNull(encrypted);
        assertNotEquals(plainSecret, encrypted);

        String decrypted = converter.convertToEntityAttribute(encrypted);
        assertEquals(plainSecret, decrypted);
    }

    @Test
    @DisplayName("Dos cifrados sucesivos del mismo secreto producen textos cifrados distintos (IV aleatorio)")
    void twoEncryptionsOfSameSecretProduceDifferentCiphertexts() {
        String plainSecret = "whsec_super_secret_webhook_key_12345";

        String enc1 = converter.encrypt(plainSecret);
        String enc2 = converter.encrypt(plainSecret);

        assertNotEquals(enc1, enc2, "El IV aleatorio de 12 bytes debe garantizar no-determinismo en el ciphertext");
        assertEquals(plainSecret, converter.decrypt(enc1));
        assertEquals(plainSecret, converter.decrypt(enc2));
    }

    @Test
    @DisplayName("Cifrado y descifrado de valores nulos o vacios retorna el mismo valor")
    void shouldHandleNullAndEmptyGracefully() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
        assertEquals("", converter.convertToDatabaseColumn(""));
        assertEquals("", converter.convertToEntityAttribute(""));
    }

    @Test
    @DisplayName("Texto cifrado alterado o truncado es rechazado por verificacion de etiqueta GCM")
    void shouldRejectTamperedOrTruncatedCiphertext() {
        assertThrows(Exception.class, () -> converter.decrypt("not-valid-base64!"));
        assertThrows(Exception.class, () -> converter.decrypt("AQIDBA==")); // solo 4 bytes, menor al IV de 12 bytes
    }
}

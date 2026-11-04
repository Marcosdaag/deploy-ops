package com.marcosdaag.deployops.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlSecurityValidatorTest {

    private UrlSecurityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UrlSecurityValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://google.com",
            "http://example.com",
            "https://1.1.1.1",
            "https://api.github.com:443",
            "http://status.mysite.com:8080",
            "https://sub.domain.co.uk/health"
    })
    @DisplayName("Acepta URLs publicas y validas")
    void whenValidPublicUrl_shouldPass(String url) {
        assertDoesNotThrow(() -> validator.validatePublicUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "ftp://ftp.is.co.za",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "gopher://gopher.floodgap.com"
    })
    @DisplayName("Rechaza esquemas no permitidos o URLs vacias")
    void whenInvalidSchemeOrEmpty_shouldThrowException(String url) {
        assertThrows(IllegalArgumentException.class, () -> validator.validatePublicUrl(url));
    }

    @Test
    @DisplayName("Rechaza URL nula")
    void whenNullUrl_shouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> validator.validatePublicUrl(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://localhost:3000",
            "http://admin.localhost",
            "http://server.local",
            "http://database.internal",
            "http://api.invalid",
            "http://router.arpa"
    })
    @DisplayName("Rechaza nombres de dominio locales o internos")
    void whenLocalhostOrInternalDomain_shouldThrowException(String url) {
        assertThrows(IllegalArgumentException.class, () -> validator.validatePublicUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1",
            "http://127.0.0.2:8080",
            "http://127.127.127.127",
            "http://10.0.0.1",
            "http://10.255.255.254",
            "http://172.16.0.1",
            "http://172.31.255.255",
            "http://192.168.0.1",
            "http://192.168.1.100"
    })
    @DisplayName("Rechaza direcciones IP privadas y loopback (RFC 1918 / RFC 1122)")
    void whenPrivateOrLoopbackIp_shouldThrowException(String url) {
        assertThrows(IllegalArgumentException.class, () -> validator.validatePublicUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254",
            "http://169.254.1.1",
            "http://169.254.169.254/latest/meta-data/"
    })
    @DisplayName("Rechaza endpoints de metadata de nube (AWS / GCP / Azure IMDS)")
    void whenCloudMetadataIp_shouldThrowException(String url) {
        assertThrows(IllegalArgumentException.class, () -> validator.validatePublicUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://100.64.0.1",
            "http://100.100.100.100",
            "http://100.127.255.255"
    })
    @DisplayName("Rechaza direcciones de Carrier-Grade NAT (RFC 6598)")
    void whenCarrierGradeNatIp_shouldThrowException(String url) {
        assertThrows(IllegalArgumentException.class, () -> validator.validatePublicUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com:22",
            "http://example.com:25",
            "http://example.com:53",
            "http://example.com:5432",
            "http://example.com:3306",
            "http://example.com:6379",
            "http://example.com:27017",
            "http://example.com:9200",
            "http://example.com:70000"
    })
    @DisplayName("Rechaza puertos de infraestructura o puertos fuera de rango")
    void whenBlockedOrInvalidPort_shouldThrowException(String url) {
        assertThrows(IllegalArgumentException.class, () -> validator.validatePublicUrl(url));
    }
}

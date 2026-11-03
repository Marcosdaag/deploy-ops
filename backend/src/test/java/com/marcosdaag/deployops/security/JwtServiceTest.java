package com.marcosdaag.deployops.security;

import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", "deployops-super-secure-secret-key-for-jwt-signing-2026-production");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L); // 1 hora

        user = new UserEntity();
        user.setId(42L);
        user.setName("Dev Tester");
        user.setEmail("dev@test.com");
        user.setRole(UserRole.ROLE_USER);
    }

    @Test
    @DisplayName("Genera y valida un token JWT correctamente")
    void whenGenerateToken_shouldExtractSubjectAndValidate() {
        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertFalse(token.isBlank());

        String email = jwtService.extractEmail(token);
        assertEquals("dev@test.com", email);

        assertTrue(jwtService.isTokenValid(token, "dev@test.com"));
        assertFalse(jwtService.isTokenValid(token, "otro@test.com"));
    }

    @Test
    @DisplayName("Identifica token expirado como invalido")
    void whenTokenExpired_shouldThrowOrReturnInvalid() {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1000L); // Ya expirado al crearse
        String expiredToken = jwtService.generateToken(user);

        assertThrows(Exception.class, () -> jwtService.isTokenValid(expiredToken, "dev@test.com"));
    }
}

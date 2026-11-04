package com.marcosdaag.deployops.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
    }

    @Test
    @DisplayName("Permite peticiones dentro del limite configurado")
    void whenRequestsWithinLimit_shouldAllow() {
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiterService.tryAcquire("test-ip", 5, 60_000));
        }
    }

    @Test
    @DisplayName("Rechaza peticion que excede el limite configurado")
    void whenRequestsExceedLimit_shouldBlock() {
        for (int i = 0; i < 3; i++) {
            assertTrue(rateLimiterService.tryAcquire("test-ip-blocked", 3, 60_000));
        }
        // Cuarta peticion debe ser rechazada
        assertFalse(rateLimiterService.tryAcquire("test-ip-blocked", 3, 60_000));
    }

    @Test
    @DisplayName("Permite peticiones tras expirar la ventana de tiempo")
    void whenWindowExpires_shouldResetAndAllow() throws InterruptedException {
        assertTrue(rateLimiterService.tryAcquire("expiring-ip", 1, 50));
        assertFalse(rateLimiterService.tryAcquire("expiring-ip", 1, 50));

        Thread.sleep(70);

        assertTrue(rateLimiterService.tryAcquire("expiring-ip", 1, 50));
    }

    @Test
    @DisplayName("Reset borra todas las cuotas activas")
    void whenReset_shouldClearBuckets() {
        assertFalse(!rateLimiterService.tryAcquire("reset-ip", 1, 60_000));
        assertFalse(rateLimiterService.tryAcquire("reset-ip", 1, 60_000));

        rateLimiterService.reset();

        assertTrue(rateLimiterService.tryAcquire("reset-ip", 1, 60_000));
    }
}

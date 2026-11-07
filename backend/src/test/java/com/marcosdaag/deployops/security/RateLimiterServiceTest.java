package com.marcosdaag.deployops.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Test
    @DisplayName("RateLimitingFilter bloquea con HTTP 429 y cuerpo JSON valido al exceder cuota en endpoints protegidos")
    void whenRateLimitExceededInFilter_shouldReturn429JsonResponse() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(rateLimiterService);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/demo");
            req.setRemoteAddr("203.0.113.10");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertEquals(200, res.getStatus());
        }

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/auth/demo");
        blockedReq.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        FilterChain blockedChain = mock(FilterChain.class);

        filter.doFilter(blockedReq, blockedRes, blockedChain);

        assertEquals(429, blockedRes.getStatus());
        assertEquals("60", blockedRes.getHeader("Retry-After"));
        assertTrue(blockedRes.getContentAsString().contains("Demasiadas solicitudes"));
        verify(blockedChain, never()).doFilter(blockedReq, blockedRes);
    }
}

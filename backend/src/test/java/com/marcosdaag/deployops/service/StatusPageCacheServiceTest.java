package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.PublicServiceDTO;
import com.marcosdaag.deployops.dto.StatusPageDTO;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusPageCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SimpleMeterRegistry meterRegistry;
    private StatusPageCacheService cacheService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cacheService = new StatusPageCacheService(redisTemplate, meterRegistry, true, 60L);
    }

    @Test
    @DisplayName("Retorna vacio y permanece inactivo cuando Redis esta deshabilitado")
    void whenRedisDisabled_shouldBypassCache() {
        StatusPageCacheService disabledCache = new StatusPageCacheService(redisTemplate, meterRegistry, false, 60L);

        assertFalse(disabledCache.isRedisCacheActive());
        assertTrue(disabledCache.get("demo").isEmpty());
        disabledCache.put("demo", new StatusPageDTO());
        disabledCache.evictBySlug("demo");

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Almacena StatusPageDTO serializado con TTL y lo recupera en un Cache Hit")
    void whenPutAndGet_shouldSerializeAndDeserializeCleanly() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        PublicServiceDTO svc = PublicServiceDTO.builder()
                .id(7L)
                .name("Core Billing API")
                .urlMasked("billing.example.com")
                .status(ServiceStatus.HEALTHY)
                .lastResponseTimeMs(42L)
                .uptimePercentage(99.98)
                .build();

        StatusPageDTO dto = StatusPageDTO.builder()
                .slug("acme-sre")
                .ownerName("Acme SRE")
                .overallStatus("OPERATIONAL")
                .overallUptime(99.98)
                .services(List.of(svc))
                .recentIncidents(Collections.emptyList())
                .build();

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        cacheService.put("Acme-SRE", dto);

        verify(valueOperations).set(eq("deployops:status_page:acme-sre"), jsonCaptor.capture(), eq(Duration.ofSeconds(60)));

        when(valueOperations.get("deployops:status_page:acme-sre")).thenReturn(jsonCaptor.getValue());

        Optional<StatusPageDTO> cached = cacheService.get("acme-sre");

        assertTrue(cached.isPresent());
        assertEquals("acme-sre", cached.get().getSlug());
        assertEquals("Acme SRE", cached.get().getOwnerName());
        assertEquals(1, cached.get().getServices().size());
        assertEquals("Core Billing API", cached.get().getServices().getFirst().getName());
        assertEquals(1L, cacheService.getHits());
        assertEquals(1.0, meterRegistry.get("deployops.cache.status_page.hits").counter().count());
    }

    @Test
    @DisplayName("Registra Cache Miss cuando la clave no existe en Redis")
    void whenKeyMissing_shouldRecordMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("deployops:status_page:demo")).thenReturn(null);

        Optional<StatusPageDTO> result = cacheService.get("demo");

        assertTrue(result.isEmpty());
        assertEquals(1L, cacheService.getMisses());
    }

    @Test
    @DisplayName("Invalida clave 'demo' para servicios showcase y slug privado para servicios de usuario")
    void whenEvictForService_shouldTargetProperSlugKey() {
        ServiceEntity showcaseService = new ServiceEntity();
        showcaseService.setUser(null);

        UserEntity owner = new UserEntity();
        owner.setPublicSlug("marcos-cloud");
        ServiceEntity userService = new ServiceEntity();
        userService.setUser(owner);

        cacheService.evictForService(showcaseService);
        cacheService.evictForService(userService);

        verify(redisTemplate).delete("deployops:status_page:demo");
        verify(redisTemplate).delete("deployops:status_page:marcos-cloud");
        assertEquals(2L, cacheService.getEvictions());
    }

    @Test
    @DisplayName("Opera en modo Fail-Open sin propagar excepciones si Redis cae")
    void whenRedisThrowsException_shouldFailOpenGracefully() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        assertDoesNotThrow(() -> {
            Optional<StatusPageDTO> res = cacheService.get("demo");
            assertTrue(res.isEmpty());
            cacheService.put("demo", StatusPageDTO.builder().slug("demo").build());
            cacheService.evictBySlug("demo");
        });
    }
}

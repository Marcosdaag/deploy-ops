package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.PingResult;
import com.marcosdaag.deployops.dto.ServiceMetricsHistoryDTO;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
import com.marcosdaag.deployops.model.PingLogEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.repository.PingLogRepository;
import com.marcosdaag.deployops.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsHistoryServiceTest {

    @Mock
    private PingLogRepository pingLogRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private MetricsHistoryService metricsHistoryService;

    private ServiceEntity service;

    @BeforeEach
    void setUp() {
        service = new ServiceEntity();
        service.setId(1L);
        service.setName("Auth Service");
        service.setUrl("https://auth.example.com/health");
        service.setPublicStatus(true);
    }

    @Test
    @DisplayName("Guarda un registro de ping correctamente")
    void whenRecordPing_shouldSavePingLogEntity() {
        PingResult result = PingResult.success(200, 150L);

        metricsHistoryService.recordPing(service, result);

        ArgumentCaptor<PingLogEntity> captor = ArgumentCaptor.forClass(PingLogEntity.class);
        verify(pingLogRepository).save(captor.capture());

        PingLogEntity saved = captor.getValue();
        assertEquals(service, saved.getService());
        assertEquals(150L, saved.getResponseTimeMs());
        assertEquals(200, saved.getStatusCode());
        assertTrue(saved.isAlive());
    }

    @Test
    @DisplayName("Calcula metricas agregadas (min, max, avg, p95, uptime) correctamente")
    void whenGetMetricsHistory_shouldComputeAggregates() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));

        List<PingLogEntity> logs = new ArrayList<>();
        long[] latencies = {100L, 120L, 150L, 180L, 200L, 250L, 300L, 400L, 500L, 1000L};
        for (long lat : latencies) {
            PingLogEntity log = new PingLogEntity();
            log.setService(service);
            log.setResponseTimeMs(lat);
            log.setStatusCode(200);
            log.setAlive(true);
            log.setTimestamp(LocalDateTime.now().minusMinutes(lat));
            logs.add(log);
        }

        // Agregar 1 ping fallido
        PingLogEntity failedLog = new PingLogEntity();
        failedLog.setService(service);
        failedLog.setResponseTimeMs(0L);
        failedLog.setStatusCode(500);
        failedLog.setAlive(false);
        failedLog.setTimestamp(LocalDateTime.now());
        logs.add(failedLog);

        when(pingLogRepository.findByServiceAndTimestampAfterOrderByTimestampAsc(eq(service), any(LocalDateTime.class)))
                .thenReturn(logs);

        ServiceMetricsHistoryDTO metrics = metricsHistoryService.getMetricsHistory(1L, "24h", null);

        assertNotNull(metrics);
        assertEquals(1L, metrics.getServiceId());
        assertEquals("Auth Service", metrics.getServiceName());
        assertEquals(100L, metrics.getMinLatencyMs());
        assertEquals(1000L, metrics.getMaxLatencyMs());
        assertEquals(1000L, metrics.getP95LatencyMs());
        assertEquals(11, metrics.getTotalPings());
        // 10 exitosos de 11 => 90.91%
        assertEquals(90.91, metrics.getUptimePercentage());
        assertEquals(11, metrics.getPoints().size());
    }

    @Test
    @DisplayName("Lanza ResourceNotFoundException si el servicio no existe")
    void whenServiceNotFound_shouldThrowException() {
        when(serviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> metricsHistoryService.getMetricsHistory(99L, "24h", null));
    }

    @Test
    @DisplayName("Lanza SecurityException si el servicio es privado y el usuario no es el dueno")
    void whenServiceIsPrivateAndUserNotOwner_shouldThrowSecurityException() {
        UserEntity owner = new UserEntity();
        owner.setId(5L);
        owner.setEmail("owner@example.com");

        service.setUser(owner);
        service.setPublicStatus(false);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));

        assertThrows(SecurityException.class, () -> metricsHistoryService.getMetricsHistory(1L, "24h", "other@example.com"));
    }

    @Test
    @DisplayName("Purga registros antiguos de mas de 30 dias")
    void whenPurgeOldLogs_shouldInvokeRepositoryDelete() {
        metricsHistoryService.purgeOldLogs();

        verify(pingLogRepository).deleteByTimestampBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Soporta el rango de consulta de 30 dias (30d)")
    void whenGetMetricsHistory30d_shouldQueryThirtyDaysWindow() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(pingLogRepository.findByServiceAndTimestampAfterOrderByTimestampAsc(eq(service), any(LocalDateTime.class)))
                .thenReturn(List.of());

        ServiceMetricsHistoryDTO metrics = metricsHistoryService.getMetricsHistory(1L, "30d", null);

        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(pingLogRepository).findByServiceAndTimestampAfterOrderByTimestampAsc(eq(service), thresholdCaptor.capture());

        LocalDateTime expectedApprox = LocalDateTime.now().minusDays(30);
        assertTrue(thresholdCaptor.getValue().isBefore(LocalDateTime.now().minusDays(29)));
        assertTrue(thresholdCaptor.getValue().isAfter(expectedApprox.minusMinutes(5)));
        assertEquals("30d", metrics.getRange());
    }
}

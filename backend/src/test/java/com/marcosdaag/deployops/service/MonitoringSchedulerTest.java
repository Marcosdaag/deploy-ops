package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.PingResult;
import com.marcosdaag.deployops.model.ServiceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonitoringSchedulerTest {

    @Mock
    private EndpointManagerService endpointManager;

    @Mock
    private HttpMonitorService httpMonitor;

    @Mock
    private SseNotificationService sseNotificationService;

    private MonitoringScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MonitoringScheduler(endpointManager, httpMonitor, sseNotificationService);
    }

    @Test
    @DisplayName("Ejecuta monitoreo concurrente para todos los endpoints registrados")
    void whenCheckingAllEndpoints_shouldPingAllConcurrentlyAndBroadcast() {
        ServiceEntity s1 = new ServiceEntity();
        s1.setId(1L);
        s1.setName("Service Alpha");
        s1.setUrl("https://alpha.example.com");

        ServiceEntity s2 = new ServiceEntity();
        s2.setId(2L);
        s2.setName("Service Beta");
        s2.setUrl("https://beta.example.com");

        when(endpointManager.getAllEndpoints()).thenReturn(List.of(s1, s2));
        when(httpMonitor.ping(any(ServiceEntity.class))).thenReturn(PingResult.success(200, 50L));

        scheduler.checkAllEndpoints();

        verify(httpMonitor).ping(s1);
        verify(httpMonitor).ping(s2);
        verify(endpointManager).processPingResult(eq(s1), any(PingResult.class));
        verify(endpointManager).processPingResult(eq(s2), any(PingResult.class));
        verify(sseNotificationService).broadcastDashboardUpdate(any());
    }

    @Test
    @DisplayName("Maneja lista vacia de endpoints sin disparar pings ni notificaciones innecesarias")
    void whenNoEndpointsConfigured_shouldReturnEarly() {
        when(endpointManager.getAllEndpoints()).thenReturn(List.of());

        scheduler.checkAllEndpoints();

        verify(httpMonitor, never()).ping(any(ServiceEntity.class));
        verify(sseNotificationService, never()).broadcastDashboardUpdate(any());
    }

    @Test
    @DisplayName("Apagado ordenado invoca shutdown y espera la terminacion de hilos")
    void whenShutdownMonitoringExecutor_shouldShutdownAndAwaitTermination() throws InterruptedException {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(10, TimeUnit.SECONDS)).thenReturn(true);

        MonitoringScheduler customScheduler = new MonitoringScheduler(
                endpointManager,
                httpMonitor,
                sseNotificationService,
                mockExecutor
        );

        customScheduler.shutdownMonitoringExecutor();

        verify(mockExecutor).shutdown();
        verify(mockExecutor).awaitTermination(10, TimeUnit.SECONDS);
        verify(mockExecutor, never()).shutdownNow();
    }

    @Test
    @DisplayName("Si el apagado excede el tiempo de espera, fuerza el cierre con shutdownNow")
    void whenAwaitTerminationTimesOut_shouldShutdownNow() throws InterruptedException {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(10, TimeUnit.SECONDS)).thenReturn(false);

        MonitoringScheduler customScheduler = new MonitoringScheduler(
                endpointManager,
                httpMonitor,
                sseNotificationService,
                mockExecutor
        );

        customScheduler.shutdownMonitoringExecutor();

        verify(mockExecutor).shutdown();
        verify(mockExecutor).awaitTermination(10, TimeUnit.SECONDS);
        verify(mockExecutor).shutdownNow();
    }

    @Test
    @DisplayName("Si el hilo es interrumpido durante el apagado, fuerza shutdownNow y restablece interrupt flag")
    void whenShutdownInterrupted_shouldShutdownNowAndRestoreInterruptFlag() throws InterruptedException {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(10, TimeUnit.SECONDS)).thenThrow(new InterruptedException("Interrupted"));

        MonitoringScheduler customScheduler = new MonitoringScheduler(
                endpointManager,
                httpMonitor,
                sseNotificationService,
                mockExecutor
        );

        customScheduler.shutdownMonitoringExecutor();

        verify(mockExecutor).shutdown();
        verify(mockExecutor).shutdownNow();
        assertTrue(Thread.interrupted());
    }

    @Test
    @DisplayName("Apagado en scheduler por defecto apaga exitosamente el ExecutorService real")
    void whenShutdownDefaultScheduler_shouldTerminateExecutor() {
        scheduler.shutdownMonitoringExecutor();
        assertTrue(scheduler.getMonitoringExecutor().isShutdown());
    }
}

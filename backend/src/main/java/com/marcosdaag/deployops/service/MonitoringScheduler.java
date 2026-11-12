package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.PingResult;
import com.marcosdaag.deployops.model.ServiceEntity;
import jakarta.annotation.PreDestroy;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class MonitoringScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final EndpointManagerService endpointManager;
    private final HttpMonitorService httpMonitor;
    private final SseNotificationService sseNotificationService;
    private final ExecutorService monitoringExecutor;

    @Autowired
    public MonitoringScheduler(
            EndpointManagerService endpointManager,
            HttpMonitorService httpMonitor,
            SseNotificationService sseNotificationService
    ) {
        this(endpointManager, httpMonitor, sseNotificationService, Executors.newVirtualThreadPerTaskExecutor());
    }

    MonitoringScheduler(
            EndpointManagerService endpointManager,
            HttpMonitorService httpMonitor,
            SseNotificationService sseNotificationService,
            ExecutorService monitoringExecutor
    ) {
        this.endpointManager = endpointManager;
        this.httpMonitor = httpMonitor;
        this.sseNotificationService = sseNotificationService;
        this.monitoringExecutor = monitoringExecutor;
    }

    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "monitoringSweepLock", lockAtMostFor = "50s", lockAtLeastFor = "15s")
    public void checkAllEndpoints() {
        List<ServiceEntity> endpoints = endpointManager.getAllEndpoints();
        if (endpoints.isEmpty()) {
            return;
        }

        logger.info("Iniciando ciclo de monitoreo concurrente para {} servicios...", endpoints.size());

        List<CompletableFuture<Void>> futures = endpoints.stream()
                .map(service -> CompletableFuture.runAsync(() -> {
                    try {
                        PingResult result = httpMonitor.ping(service);
                        endpointManager.processPingResult(service, result);
                    } catch (Exception e) {
                        logger.error("Error inesperado al monitorear {}: {}", service.getName(), e.getMessage());
                    }
                }, monitoringExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        sseNotificationService.broadcastDashboardUpdate(endpointManager.getDashboardData());
        logger.info("Ciclo de monitoreo concurrente completado y notificado por SSE.");
    }

    @PreDestroy
    public void shutdownMonitoringExecutor() {
        logger.info("Iniciando apagado ordenado de monitoringExecutor...");
        monitoringExecutor.shutdown();
        try {
            if (!monitoringExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("monitoringExecutor no finalizo en 10 segundos, forzando apagado...");
                monitoringExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.warn("Apagado de monitoringExecutor interrumpido, forzando apagado...");
            monitoringExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("monitoringExecutor apagado correctamente.");
    }

    ExecutorService getMonitoringExecutor() {
        return monitoringExecutor;
    }
}

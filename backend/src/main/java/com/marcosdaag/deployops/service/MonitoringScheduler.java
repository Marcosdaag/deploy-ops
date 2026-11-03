package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.PingResult;
import com.marcosdaag.deployops.model.ServiceEntity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MonitoringScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final EndpointManagerService endpointManager;
    private final HttpMonitorService httpMonitor;
    private final SseNotificationService sseNotificationService;

    @Scheduled(fixedDelay = 60000)
    public void checkAllEndpoints() {
        List<ServiceEntity> endpoints = endpointManager.getAllEndpoints();
        if (endpoints.isEmpty()) {
            return;
        }

        logger.info("Iniciando ciclo de monitoreo para {} servicios...", endpoints.size());

        for (ServiceEntity service : endpoints) {
            try {
                PingResult result = httpMonitor.ping(service.getUrl());
                endpointManager.processPingResult(service, result);
            } catch (Exception e) {
                logger.error("Error inesperado al monitorear {}: {}", service.getName(), e.getMessage());
            }
        }

        sseNotificationService.broadcastDashboardUpdate(endpointManager.getDashboardData());
        logger.info("Ciclo de monitoreo completado y notificado por SSE.");
    }
}

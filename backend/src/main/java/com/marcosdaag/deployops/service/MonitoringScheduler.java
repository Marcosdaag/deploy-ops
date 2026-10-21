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

    // Cada 1 minuto (60s despues de terminar el anterior) chequea todos los servicios
    @Scheduled(fixedDelay = 60000)
    public void checkAllServices() {
        logger.info("CRON verificando el estado de los servicios.");

        List<ServiceEntity> services = endpointManager.getAllEndpoints();

        for (ServiceEntity service : services) {
            try {
                PingResult result = httpMonitor.ping(service.getUrl());

                if (result.isAlive()) {
                    logger.info("[UP - {}ms] {} ({}) responde correctamente.",
                            result.responseTimeMs(), service.getName(), service.getUrl());
                } else {
                    logger.error("[DOWN - {}ms] {} ({}) fallo: {}",
                            result.responseTimeMs(), service.getName(), service.getUrl(), result.errorMessage());
                }

                endpointManager.processPingResult(service, result.isAlive());

            } catch (Exception e) {
                logger.error("Error al chequear servicio {}: {}", service.getName(), e.getMessage());
            }
        }

        logger.info("CRON verificacion terminada.");
    }
}

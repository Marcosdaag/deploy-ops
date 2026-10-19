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

    /**
     * Tarea programada que ejecuta health checks periódicos.
     * fixedDelay = 60000 garantiza que pasen 60s tras TERMINAR la ejecución previa,
     * evitando acumulación o solapamiento de tareas en caso de lentitud en la red.
     */
    @Scheduled(fixedDelay = 60000)
    public void checkAllServices() {
        logger.info("Iniciando ciclo de monitoreo de servicios...");

        List<ServiceEntity> services = endpointManager.getAllEndpoints();

        for (ServiceEntity service : services) {
            try {
                PingResult result = httpMonitor.ping(service.getUrl());

                if (result.isAlive()) {
                    logger.info("✅ [HTTP {} - {}ms] {} ({}) responde correctamente.",
                            result.statusCode(), result.responseTimeMs(), service.getName(), service.getUrl());
                } else {
                    logger.warn("⚠️ [FALLO - {}ms] {} ({}): {}",
                            result.responseTimeMs(), service.getName(), service.getUrl(), result.errorMessage());
                }

                endpointManager.processPingResult(service, result.isAlive());

            } catch (Exception e) {
                logger.error("Error inesperado al monitorear servicio {}: {}", service.getName(), e.getMessage());
            }
        }

        logger.info("Ciclo de monitoreo finalizado. Servicios evaluados: {}", services.size());
    }
}
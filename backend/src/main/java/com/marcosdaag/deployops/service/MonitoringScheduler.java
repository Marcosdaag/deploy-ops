package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.ServiceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MonitoringScheduler {

    // Instanciamos el logger
    private static final Logger logger = LoggerFactory.getLogger(MonitoringScheduler.class);

    @Autowired
    private EndpointManagerService endpointManager;

    @Autowired
    private HttpMonitorService httpMonitor;

    // Cada 1 minuto hace un check de todos los servicios
    @Scheduled(fixedRate = 60000)
    public void checkAllServices() {
        logger.info("CRON verificando el estado de los servicos.");

        List<ServiceEntity> services = endpointManager.getAllEndpoints();

        for (ServiceEntity service : services) {
            String url = service.getUrl();
            boolean isAlive = httpMonitor.isUrlAlive(url);

            if (isAlive) {
                logger.info("✅ [UP] {} ({}) responde correctamente.", service.getName(), url);
            } else {
                logger.error("❌ [DOWN] {} ({}) está caido.", service.getName(), url);
            }

            endpointManager.processPingResult(service, isAlive);
        }

        logger.info("CRON verificacion terminada.");
    }
}
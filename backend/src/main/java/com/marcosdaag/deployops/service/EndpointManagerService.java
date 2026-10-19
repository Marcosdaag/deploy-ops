package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.EndpointCreateDTO;
import com.marcosdaag.deployops.dto.EndpointResponseDTO;
import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.enums.IncidentStatus;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.repository.IncidentRepository;
import com.marcosdaag.deployops.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EndpointManagerService {

    private static final Logger logger = LoggerFactory.getLogger(EndpointManagerService.class);

    private final ServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final MetricsService metricsService;

    // Crea un nuevo servicio monitoreado
    @Transactional
    public ServiceEntity createEndpoint(@NonNull EndpointCreateDTO dto) {
        ServiceEntity newEndpoint = new ServiceEntity();
        newEndpoint.setName(dto.getName());
        newEndpoint.setUrl(dto.getUrl());
        newEndpoint.setStatus(ServiceStatus.HEALTHY);
        newEndpoint.setConsecutiveFailures(0);
        newEndpoint.setConsecutiveSuccesses(0);
        newEndpoint.setCreatedAt(LocalDateTime.now());

        return serviceRepository.save(newEndpoint);
    }

    @Transactional
    public void deleteEndpoint(Long id) {
        serviceRepository.deleteById(id);
    }

    public List<ServiceEntity> getAllEndpoints() {
        return serviceRepository.findAll();
    }

    public List<EndpointResponseDTO> getDashboardData() {
        List<ServiceEntity> services = serviceRepository.findAll();
        return services.stream()
                .map(service -> {
                    double uptime = metricsService.calculateUptimePercentage(service);
                    return new EndpointResponseDTO(service, uptime);
                })
                .toList();
    }

    /**
     * Máquina de Estados para procesar el resultado de cada ping:
     * - HEALTHY -> DEGRADED (1 fallo)
     * - DEGRADED -> DOWN (3 fallos consecutivos, abre incidente)
     * - DEGRADED -> HEALTHY (1 éxito, falsa alarma)
     * - DOWN -> RECOVERING (1er éxito)
     * - RECOVERING -> DOWN (1 fallo, recaída)
     * - RECOVERING -> HEALTHY (2 éxitos consecutivos, cierra incidente)
     */
    @Transactional
    public void processPingResult(ServiceEntity service, boolean isAlive) {
        service.setLastCheckedAt(LocalDateTime.now());

        if (isAlive) {
            handleSuccessfulPing(service);
        } else {
            handleFailedPing(service);
        }

        serviceRepository.save(service);
    }

    private void handleSuccessfulPing(ServiceEntity service) {
        service.setConsecutiveFailures(0);
        service.setConsecutiveSuccesses(service.getConsecutiveSuccesses() + 1);

        switch (service.getStatus()) {
            case DEGRADED -> {
                logger.info("Servicio {} recuperado de estado degradado (falsa alarma). Volviendo a HEALTHY.", service.getName());
                service.setStatus(ServiceStatus.HEALTHY);
            }
            case DOWN -> {
                logger.info("Servicio {} respondió por primera vez tras la caída. Pasando a RECOVERING.", service.getName());
                service.setStatus(ServiceStatus.RECOVERING);
            }
            case RECOVERING -> {
                if (service.getConsecutiveSuccesses() >= 2) {
                    logger.info("Servicio {} confirmó 2 éxitos consecutivos. Cerrando incidente y volviendo a HEALTHY.", service.getName());
                    service.setStatus(ServiceStatus.HEALTHY);
                    closeActiveIncident(service);
                }
            }
            case HEALTHY -> {
                // Sigue funcionando correctamente
            }
        }
    }

    private void handleFailedPing(ServiceEntity service) {
        service.setConsecutiveSuccesses(0);
        service.setConsecutiveFailures(service.getConsecutiveFailures() + 1);

        switch (service.getStatus()) {
            case HEALTHY -> {
                logger.warn("Primer fallo detectado en {}. Marcando como DEGRADED.", service.getName());
                service.setStatus(ServiceStatus.DEGRADED);
            }
            case DEGRADED -> {
                if (service.getConsecutiveFailures() >= 3) {
                    logger.error("Servicio {} acumuló 3 fallos consecutivos. Marcando como DOWN y abriendo incidente.", service.getName());
                    service.setStatus(ServiceStatus.DOWN);
                    openNewIncident(service);
                } else {
                    logger.warn("Servicio {} continúa fallando (intento {}/3).", service.getName(), service.getConsecutiveFailures());
                }
            }
            case RECOVERING -> {
                logger.warn("Servicio {} recayó durante su recuperación. Volviendo a DOWN.", service.getName());
                service.setStatus(ServiceStatus.DOWN);
            }
            case DOWN -> {
                // Ya se encuentra en estado caído con incidente abierto
            }
        }
    }

    private void openNewIncident(ServiceEntity service) {
        IncidentEntity newIncident = new IncidentEntity();
        newIncident.setService(service);
        newIncident.setStatus(IncidentStatus.ONGOING);
        newIncident.setStartedAt(LocalDateTime.now());
        incidentRepository.save(newIncident);
    }

    private void closeActiveIncident(ServiceEntity service) {
        incidentRepository.findByServiceAndStatus(service, IncidentStatus.ONGOING)
                .ifPresent(incident -> {
                    incident.setStatus(IncidentStatus.RESOLVED);
                    incident.setResolvedAt(LocalDateTime.now());
                    incidentRepository.save(incident);
                });
    }
}
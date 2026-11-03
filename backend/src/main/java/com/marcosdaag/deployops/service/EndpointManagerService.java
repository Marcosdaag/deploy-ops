package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.EndpointCreateDTO;
import com.marcosdaag.deployops.dto.EndpointResponseDTO;
import com.marcosdaag.deployops.dto.IncidentResponseDTO;
import com.marcosdaag.deployops.dto.PingResult;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EndpointManagerService {

    private static final Logger logger = LoggerFactory.getLogger(EndpointManagerService.class);

    private final ServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final MetricsService metricsService;
    private final UrlSecurityValidator urlSecurityValidator;
    private final HttpMonitorService httpMonitorService;
    private final SseNotificationService sseNotificationService;

    @Transactional
    public ServiceEntity createEndpoint(@NonNull EndpointCreateDTO dto) {
        String cleanUrl = dto.getUrl().trim();

        urlSecurityValidator.validatePublicUrl(cleanUrl);

        if (serviceRepository.existsByUrl(cleanUrl)) {
            throw new IllegalArgumentException("Ya existe un servicio registrado con esa URL.");
        }

        ServiceEntity newEndpoint = new ServiceEntity();
        newEndpoint.setName(dto.getName().trim());
        newEndpoint.setUrl(cleanUrl);
        newEndpoint.setStatus(ServiceStatus.PENDING);
        newEndpoint.setConsecutiveFailures(0);
        newEndpoint.setConsecutiveSuccesses(0);
        newEndpoint.setCreatedAt(LocalDateTime.now());
        newEndpoint.setProtected(false);

        ServiceEntity saved = serviceRepository.save(newEndpoint);
        sseNotificationService.broadcastDashboardUpdate(getDashboardData());
        return saved;
    }

    @Transactional
    public void deleteEndpoint(Long id) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + id));

        if (service.isProtected() || esUrlProtegida(service.getUrl())) {
            throw new IllegalArgumentException("Este endpoint de demostracion esta protegido contra eliminacion.");
        }

        incidentRepository.deleteByServiceId(id);
        serviceRepository.deleteById(id);

        sseNotificationService.broadcastDashboardUpdate(getDashboardData());
    }

    public List<ServiceEntity> getAllEndpoints() {
        return serviceRepository.findAll();
    }

    // Precalcula uptimes en lote para evitar consultas N+1
    public List<EndpointResponseDTO> getDashboardData() {
        List<ServiceEntity> services = serviceRepository.findAll();
        Map<Long, Double> uptimes = metricsService.calculateUptimesForServices(services);

        return services.stream()
                .map(service -> {
                    double uptime = uptimes.getOrDefault(service.getId(), 0.0);
                    return new EndpointResponseDTO(service, uptime);
                })
                .toList();
    }

    public List<IncidentResponseDTO> getIncidentsForService(Long serviceId) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + serviceId));

        return incidentRepository.findByServiceOrderByStartedAtDesc(service)
                .stream()
                .map(IncidentResponseDTO::new)
                .toList();
    }

    @Transactional
    public EndpointResponseDTO triggerManualPing(Long id) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + id));

        PingResult pingResult = httpMonitorService.ping(service.getUrl());
        processPingResult(service, pingResult);

        double uptime = metricsService.calculateUptimePercentage(service);
        EndpointResponseDTO response = new EndpointResponseDTO(service, uptime);

        sseNotificationService.broadcastDashboardUpdate(getDashboardData());
        return response;
    }

    @Transactional
    public void processPingResult(ServiceEntity service, PingResult pingResult) {
        // Descarta actualizacion si el servicio fue eliminado durante el ping
        if (service.getId() != null && !serviceRepository.existsById(service.getId())) {
            logger.warn("El servicio {} (ID {}) ya no existe en la base de datos. Se descarta la actualizacion.", service.getName(), service.getId());
            return;
        }

        service.setLastCheckedAt(LocalDateTime.now());
        service.setLastResponseTimeMs(pingResult.responseTimeMs());

        if (pingResult.isAlive()) {
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
            case PENDING -> {
                logger.info("Primer ping exitoso en {}. Pasa a HEALTHY.", service.getName());
                service.setStatus(ServiceStatus.HEALTHY);
            }
            case DEGRADED -> {
                logger.info("Servicio {} recuperado de estado degradado. Volviendo a HEALTHY.", service.getName());
                service.setStatus(ServiceStatus.HEALTHY);
            }
            case DOWN -> {
                logger.info("Servicio {} respondio tras la caida. Pasando a RECOVERING.", service.getName());
                service.setStatus(ServiceStatus.RECOVERING);
            }
            case RECOVERING -> {
                // 2 exitos consecutivos confirman recuperacion y cierran el incidente
                if (service.getConsecutiveSuccesses() >= 2) {
                    logger.info("Servicio {} confirmo 2 exitos seguidos. Cerrando incidente.", service.getName());
                    service.setStatus(ServiceStatus.HEALTHY);
                    closeOngoingIncident(service);
                }
            }
            case HEALTHY -> {
                // Sigue en operacion nominal
            }
        }
    }

    private void handleFailedPing(ServiceEntity service) {
        service.setConsecutiveSuccesses(0);
        service.setConsecutiveFailures(service.getConsecutiveFailures() + 1);

        switch (service.getStatus()) {
            case PENDING, HEALTHY -> {
                logger.warn("Primer fallo en {}. Marcando como DEGRADED.", service.getName());
                service.setStatus(ServiceStatus.DEGRADED);
            }
            case DEGRADED -> {
                // Al tercer fallo consecutivo se confirma caida y se abre incidente
                if (service.getConsecutiveFailures() >= 3) {
                    logger.error("Servicio {} acumulo 3 fallos seguidos. Marcando como DOWN y abriendo incidente.", service.getName());
                    service.setStatus(ServiceStatus.DOWN);
                    openNewIncident(service);
                } else {
                    logger.warn("Servicio {} sigue fallando (intento {}/3).", service.getName(), service.getConsecutiveFailures());
                }
            }
            case DOWN -> {
                logger.warn("Servicio {} permanece en estado DOWN.", service.getName());
            }
            case RECOVERING -> {
                logger.warn("Servicio {} volvio a caer. Volviendo a DOWN.", service.getName());
                service.setStatus(ServiceStatus.DOWN);
            }
        }
    }

    private void openNewIncident(ServiceEntity service) {
        incidentRepository.findByServiceAndStatus(service, IncidentStatus.ONGOING)
                .ifPresentOrElse(
                        inc -> logger.warn("Ya existe un incidente activo para el servicio ID {}", service.getId()),
                        () -> {
                            IncidentEntity incident = new IncidentEntity();
                            incident.setService(service);
                            incident.setStatus(IncidentStatus.ONGOING);
                            incident.setStartedAt(LocalDateTime.now());
                            incidentRepository.save(incident);
                        }
                );
    }

    private void closeOngoingIncident(ServiceEntity service) {
        incidentRepository.findByServiceAndStatus(service, IncidentStatus.ONGOING)
                .ifPresent(incident -> {
                    incident.setStatus(IncidentStatus.RESOLVED);
                    incident.setResolvedAt(LocalDateTime.now());
                    incidentRepository.save(incident);
                });
    }

    private boolean esUrlProtegida(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("google.com") || lower.contains("1.1.1.1") || lower.contains("wikipedia.org");
    }
}

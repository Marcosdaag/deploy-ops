package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.EndpointCreateDTO;
import com.marcosdaag.deployops.dto.EndpointResponseDTO;
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

    // Inyecciones por constructor
    private final ServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final MetricsService metricsService;
    private final UrlSecurityValidator urlSecurityValidator;

    // Crea un "servicio" o endpoint a vigilar
    @Transactional
    public ServiceEntity createEndpoint(@NonNull EndpointCreateDTO dto) {
        String cleanUrl = dto.getUrl().trim();

        // 1. Validamos que la URL sea segura y no apunte a redes internas (SSRF)
        urlSecurityValidator.validatePublicUrl(cleanUrl);

        // 2. Validamos que no se registre dos veces la misma URL
        if (serviceRepository.existsByUrl(cleanUrl)) {
            throw new IllegalArgumentException("Ya existe un servicio registrado con esa URL.");
        }

        ServiceEntity newEndpoint = new ServiceEntity();
        newEndpoint.setName(dto.getName().trim());
        newEndpoint.setUrl(cleanUrl);
        newEndpoint.setStatus(ServiceStatus.PENDING); // Arranca en PENDING hasta el primer ping
        newEndpoint.setConsecutiveFailures(0);
        newEndpoint.setConsecutiveSuccesses(0);
        newEndpoint.setCreatedAt(LocalDateTime.now());

        return serviceRepository.save(newEndpoint);
    }

    // Borra el servicio y sus incidentes asociados
    @Transactional
    public void deleteEndpoint(Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new ResourceNotFoundException("No se encontro el servicio con ID: " + id);
        }

        incidentRepository.deleteByServiceId(id);
        serviceRepository.deleteById(id);
    }

    // Lista todos los endpoints a vigilar
    public List<ServiceEntity> getAllEndpoints() {
        return serviceRepository.findAll();
    }

    // Carga los datos del dashboard calculando los uptimes en bloque (evita problema N+1)
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

    // Evalua que hacer con el resultado del pingeo y gestiona incidentes
    @Transactional
    public void processPingResult(ServiceEntity service, PingResult pingResult) {
        // Evitamos condicion de carrera: si el servicio fue borrado mientras viajaba el ping, no lo resucitamos
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
                // Era falsa alarma, vuelve a estar sano
                logger.info("Servicio {} recuperado de estado degradado. Volviendo a HEALTHY.", service.getName());
                service.setStatus(ServiceStatus.HEALTHY);
            }
            case DOWN -> {
                // Estaba caido y respondio por primera vez
                logger.info("Servicio {} respondio tras la caida. Pasando a RECOVERING.", service.getName());
                service.setStatus(ServiceStatus.RECOVERING);
            }
            case RECOVERING -> {
                // Con 2 exitos seguidos confirmamos que levanto y cerramos el incidente
                if (service.getConsecutiveSuccesses() >= 2) {
                    logger.info("Servicio {} confirmo 2 exitos seguidos. Cerrando incidente.", service.getName());
                    service.setStatus(ServiceStatus.HEALTHY);
                    closeActiveIncident(service);
                }
            }
            case HEALTHY -> {
                // Sigue funcionando bien
            }
        }
    }

    private void handleFailedPing(ServiceEntity service) {
        service.setConsecutiveSuccesses(0);
        service.setConsecutiveFailures(service.getConsecutiveFailures() + 1);

        switch (service.getStatus()) {
            case PENDING, HEALTHY -> {
                // Al primer fallo lo pasamos a DEGRADED (sin abrir incidente todavia)
                logger.warn("Primer fallo en {}. Marcando como DEGRADED.", service.getName());
                service.setStatus(ServiceStatus.DEGRADED);
            }
            case DEGRADED -> {
                // Si llega a 3 fallos seguidos, se considera caido y abrimos incidente
                if (service.getConsecutiveFailures() >= 3) {
                    logger.error("Servicio {} acumulo 3 fallos seguidos. Marcando como DOWN y abriendo incidente.", service.getName());
                    service.setStatus(ServiceStatus.DOWN);
                    openNewIncident(service);
                } else {
                    logger.warn("Servicio {} sigue fallando (intento {}/3).", service.getName(), service.getConsecutiveFailures());
                }
            }
            case RECOVERING -> {
                // Estaba recuperandose pero volvio a fallar
                logger.warn("Servicio {} volvio a caer. Volviendo a DOWN.", service.getName());
                service.setStatus(ServiceStatus.DOWN);
            }
            case DOWN -> {
                // Ya esta caido y con incidente abierto
            }
        }
    }

    private void openNewIncident(ServiceEntity service) {
        if (incidentRepository.findByServiceAndStatus(service, IncidentStatus.ONGOING).isPresent()) {
            return;
        }

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

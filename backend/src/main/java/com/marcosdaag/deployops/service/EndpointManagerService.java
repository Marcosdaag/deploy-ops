package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.EndpointCreateDTO;
import com.marcosdaag.deployops.dto.EndpointResponseDTO;
import com.marcosdaag.deployops.dto.EndpointUpdateDTO;
import com.marcosdaag.deployops.dto.IncidentResponseDTO;
import com.marcosdaag.deployops.dto.PingResult;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.enums.IncidentStatus;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.repository.IncidentRepository;
import com.marcosdaag.deployops.repository.PingLogRepository;
import com.marcosdaag.deployops.repository.ServiceRepository;
import com.marcosdaag.deployops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EndpointManagerService {

    private static final Logger logger = LoggerFactory.getLogger(EndpointManagerService.class);

    private final ServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final PingLogRepository pingLogRepository;
    private final UserRepository userRepository;
    private final MetricsService metricsService;
    private final MetricsHistoryService metricsHistoryService;
    private final UrlSecurityValidator urlSecurityValidator;
    private final HttpMonitorService httpMonitorService;
    private final SseNotificationService sseNotificationService;
    private final EmailAlertService emailAlertService;
    private final StatusPageCacheService statusPageCacheService;

    @Transactional
    public ServiceEntity createEndpoint(@NonNull EndpointCreateDTO dto, String currentUserEmail) {
        String cleanUrl = dto.getUrl().trim();

        urlSecurityValidator.validatePublicUrl(cleanUrl);

        UserEntity currentUser = null;
        if (currentUserEmail != null && !currentUserEmail.isBlank()) {
            currentUser = userRepository.findByEmail(currentUserEmail.toLowerCase()).orElse(null);
        }

        if (currentUser != null) {
            if (serviceRepository.existsByUrlAndUser(cleanUrl, currentUser)) {
                throw new IllegalArgumentException("Ya estás monitoreando esta URL en tu cuenta.");
            }
        } else {
            if (serviceRepository.existsByUrl(cleanUrl)) {
                throw new IllegalArgumentException("Ya existe un servicio registrado con esa URL.");
            }
        }

        ServiceEntity newEndpoint = new ServiceEntity();
        newEndpoint.setName(dto.getName().trim());
        newEndpoint.setUrl(cleanUrl);
        newEndpoint.setStatus(ServiceStatus.PENDING);
        newEndpoint.setConsecutiveFailures(0);
        newEndpoint.setConsecutiveSuccesses(0);
        newEndpoint.setCreatedAt(LocalDateTime.now());
        newEndpoint.setProtected(false);
        newEndpoint.setHttpMethod(dto.getHttpMethod() != null && !dto.getHttpMethod().isBlank() ? dto.getHttpMethod().toUpperCase() : "GET");
        newEndpoint.setExpectedStatusCode(dto.getExpectedStatusCode());
        newEndpoint.setCustomHeaders(dto.getCustomHeaders());
        newEndpoint.setPublicStatus(dto.isPublicStatus());
        newEndpoint.setUser(currentUser);

        ServiceEntity saved = serviceRepository.save(newEndpoint);
        evictStatusCache(saved);
        sseNotificationService.broadcastDashboardUpdate(getDashboardData(null));
        return saved;
    }

    @Transactional
    public ServiceEntity createEndpoint(@NonNull EndpointCreateDTO dto) {
        return createEndpoint(dto, null);
    }

    @Transactional
    public void deleteEndpoint(Long id, String currentUserEmail) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + id));

        if (service.isProtected() || (service.getUser() == null && esUrlProtegida(service.getUrl()))) {
            throw new IllegalArgumentException("Este endpoint de demostracion esta protegido contra eliminacion.");
        }

        if (service.getUser() != null) {
            if (currentUserEmail == null || !service.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new SecurityException("No tienes permisos para eliminar este endpoint.");
            }
        }

        pingLogRepository.deleteByServiceId(id);
        incidentRepository.deleteByServiceId(id);
        serviceRepository.deleteById(id);
        evictStatusCache(service);

        sseNotificationService.broadcastDashboardUpdate(getDashboardData(null));
    }

    @Transactional
    public void deleteEndpoint(Long id) {
        deleteEndpoint(id, null);
    }

    @Transactional
    public EndpointResponseDTO updateEndpoint(Long id, @NonNull EndpointUpdateDTO dto, String currentUserEmail) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + id));

        if (service.isProtected() || (service.getUser() == null && esUrlProtegida(service.getUrl()))) {
            throw new IllegalArgumentException("Este endpoint de demostracion esta protegido contra modificaciones.");
        }

        if (service.getUser() != null) {
            if (currentUserEmail == null || !service.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new SecurityException("No tienes permisos para modificar este endpoint.");
            }
        }

        String cleanUrl = dto.getUrl().trim();
        urlSecurityValidator.validatePublicUrl(cleanUrl);

        if (!cleanUrl.equalsIgnoreCase(service.getUrl())) {
            if (service.getUser() != null) {
                if (serviceRepository.existsByUrlAndUser(cleanUrl, service.getUser())) {
                    throw new IllegalArgumentException("Ya estás monitoreando esta URL en tu cuenta.");
                }
            } else {
                if (serviceRepository.existsByUrl(cleanUrl)) {
                    throw new IllegalArgumentException("Ya existe otro endpoint registrado con esa URL.");
                }
            }
            service.setUrl(cleanUrl);
            service.setStatus(ServiceStatus.PENDING);
            service.setConsecutiveFailures(0);
            service.setConsecutiveSuccesses(0);
        }

        service.setName(dto.getName().trim());
        service.setHttpMethod(dto.getHttpMethod() != null && !dto.getHttpMethod().isBlank() ? dto.getHttpMethod().toUpperCase() : "GET");
        service.setExpectedStatusCode(dto.getExpectedStatusCode());
        service.setCustomHeaders(dto.getCustomHeaders());
        service.setPublicStatus(dto.isPublicStatus());

        ServiceEntity saved = serviceRepository.save(service);
        evictStatusCache(saved);
        double uptime = metricsService.calculateUptimePercentage(saved);

        sseNotificationService.broadcastDashboardUpdate(getDashboardData(null));
        return new EndpointResponseDTO(saved, uptime, currentUserEmail);
    }

    @Transactional
    public void toggleVisibility(Long id, boolean isPublic, String currentUserEmail) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + id));

        if (service.getUser() != null) {
            if (currentUserEmail == null || !service.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new SecurityException("No tienes permisos para modificar este endpoint.");
            }
        }

        service.setPublicStatus(isPublic);
        serviceRepository.save(service);
        evictStatusCache(service);
        sseNotificationService.broadcastDashboardUpdate(getDashboardData(null));
    }

    @Transactional(readOnly = true)
    public List<ServiceEntity> getAllEndpoints() {
        return serviceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<EndpointResponseDTO> getDashboardData(String currentUserEmail) {
        return getDashboardData(currentUserEmail, null);
    }

    @Transactional(readOnly = true)
    public List<EndpointResponseDTO> getDashboardData(String currentUserEmail, String scope) {
        List<ServiceEntity> services;
        if (currentUserEmail != null && !currentUserEmail.isBlank()) {
            UserEntity user = userRepository.findByEmail(currentUserEmail.toLowerCase()).orElse(null);
            if (user != null) {
                if ("showcase".equalsIgnoreCase(scope)) {
                    services = serviceRepository.findByUserIsNullOrderByIdDesc();
                } else if ("all".equalsIgnoreCase(scope)) {
                    services = serviceRepository.findShowcaseAndUserServices(user);
                } else {
                    services = serviceRepository.findByUserOrderByIdDesc(user);
                }
            } else {
                services = serviceRepository.findByUserIsNullOrderByIdDesc();
            }
        } else {
            services = serviceRepository.findByUserIsNullOrderByIdDesc();
        }

        Map<Long, Double> uptimes = metricsService.calculateUptimesForServices(services);

        return services.stream()
                .map(service -> {
                    double uptime = uptimes.getOrDefault(service.getId(), 0.0);
                    return new EndpointResponseDTO(service, uptime, currentUserEmail);
                })
                .toList();
    }

    @Transactional
    public List<EndpointResponseDTO> seedSampleEndpointsForUser(String currentUserEmail) {
        if (currentUserEmail == null || currentUserEmail.isBlank()) {
            throw new IllegalArgumentException("Debes estar autenticado para sembrar endpoints de ejemplo.");
        }

        UserEntity user = userRepository.findByEmail(currentUserEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + currentUserEmail));

        List<String[]> samples = List.of(
                new String[]{"GitHub API", "https://api.github.com"},
                new String[]{"Cloudflare Edge DNS", "https://1.1.1.1"},
                new String[]{"HttpBin Test", "https://httpbin.org/get"}
        );

        for (String[] sample : samples) {
            String name = sample[0];
            String url = sample[1];
            if (!serviceRepository.existsByUrlAndUser(url, user)) {
                ServiceEntity s = new ServiceEntity();
                s.setName(name);
                s.setUrl(url);
                s.setStatus(ServiceStatus.PENDING);
                s.setConsecutiveFailures(0);
                s.setConsecutiveSuccesses(0);
                s.setCreatedAt(LocalDateTime.now());
                s.setProtected(false);
                s.setPublicStatus(true);
                s.setHttpMethod("GET");
                s.setUser(user);
                serviceRepository.save(s);
            }
        }

        return getDashboardData(currentUserEmail);
    }

    @Transactional(readOnly = true)
    public List<EndpointResponseDTO> getDashboardData() {
        return getDashboardData(null);
    }

    @Transactional(readOnly = true)
    public List<IncidentResponseDTO> getIncidentsForService(Long serviceId, String currentUserEmail) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + serviceId));

        if (service.getUser() != null && !service.isPublicStatus()) {
            if (currentUserEmail == null || !service.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new SecurityException("No tienes permisos para ver los incidentes de este endpoint.");
            }
        }

        return incidentRepository.findByServiceOrderByStartedAtDesc(service)
                .stream()
                .map(IncidentResponseDTO::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncidentResponseDTO> getIncidentsForService(Long serviceId) {
        return getIncidentsForService(serviceId, null);
    }

    @Transactional
    public EndpointResponseDTO triggerManualPing(Long id, String currentUserEmail) {
        ServiceEntity service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + id));

        if (service.getUser() != null) {
            if (currentUserEmail == null || !service.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new SecurityException("No tienes permisos para ejecutar ping en este endpoint.");
            }
        }

        PingResult pingResult = httpMonitorService.ping(service);
        processPingResult(service, pingResult);

        double uptime = metricsService.calculateUptimePercentage(service);
        EndpointResponseDTO response = new EndpointResponseDTO(service, uptime, currentUserEmail);

        sseNotificationService.broadcastDashboardUpdate(getDashboardData(null));
        return response;
    }

    @Transactional
    public EndpointResponseDTO triggerManualPing(Long id) {
        return triggerManualPing(id, null);
    }

    @Transactional
    public void processPingResult(ServiceEntity service, PingResult pingResult) {
        if (service.getId() != null && !serviceRepository.existsById(service.getId())) {
            logger.warn("El servicio {} (ID {}) ya no existe en la base de datos. Se descarta la actualizacion.", service.getName(), service.getId());
            return;
        }

        service.setLastCheckedAt(LocalDateTime.now());
        service.setLastResponseTimeMs(pingResult.responseTimeMs());

        if (service.getUrl() != null && service.getUrl().toLowerCase().startsWith("https://")) {
            com.marcosdaag.deployops.dto.SslCertificateInfo sslInfo = httpMonitorService.checkSslCertificate(service.getUrl());
            if (sslInfo != null) {
                service.setSslDaysRemaining(sslInfo.daysRemaining());
                service.setSslExpiryDate(sslInfo.expiryDate());
                service.setSslIssuer(sslInfo.issuer());

                if (sslInfo.daysRemaining() != null && sslInfo.daysRemaining() <= 14 && service.getUser() != null && service.getUser().getEmail() != null) {
                    emailAlertService.sendSslExpiringAlert(
                            service.getUser().getEmail(),
                            service.getName(),
                            service.getUrl(),
                            sslInfo.daysRemaining(),
                            sslInfo.expiryDate()
                    );
                }
            }
        }

        metricsHistoryService.recordPing(service, pingResult);
        ServiceStatus previousStatus = service.getStatus();

        if (pingResult.isAlive()) {
            handleSuccessfulPing(service);
        } else {
            handleFailedPing(service);
        }

        serviceRepository.save(service);
        if (service.getStatus() != previousStatus) {
            evictStatusCache(service);
        }
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
                if (service.getConsecutiveSuccesses() >= 2) {
                    logger.info("Servicio {} confirmo 2 exitos seguidos. Cerrando incidente.", service.getName());
                    service.setStatus(ServiceStatus.HEALTHY);
                    closeOngoingIncident(service);
                }
            }
            case HEALTHY -> {
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

    public void triggerTestAlert(Long serviceId, String currentUserEmail) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + serviceId));

        if (service.getUser() != null && currentUserEmail != null) {
            if (!service.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new SecurityException("No tienes permisos para disparar alertas en este endpoint.");
            }
        }

        String recipientEmail = (currentUserEmail != null && !currentUserEmail.isBlank())
                ? currentUserEmail
                : (service.getUser() != null ? service.getUser().getEmail() : null);

        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("No hay una direccion de correo electronico asociada para enviar la alerta.");
        }

        emailAlertService.sendTestAlert(recipientEmail, service.getName(), service.getUrl());
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
                            IncidentEntity saved = incidentRepository.save(incident);

                            if (service.getUser() != null && service.getUser().getEmail() != null) {
                                emailAlertService.sendIncidentOpenedAlert(
                                        service.getUser().getEmail(),
                                        service.getName(),
                                        service.getUrl(),
                                        saved.getStartedAt()
                                );
                            }
                        }
                );
    }

    private void closeOngoingIncident(ServiceEntity service) {
        incidentRepository.findByServiceAndStatus(service, IncidentStatus.ONGOING)
                .ifPresent(incident -> {
                    incident.setStatus(IncidentStatus.RESOLVED);
                    incident.setResolvedAt(LocalDateTime.now());
                    IncidentEntity saved = incidentRepository.save(incident);

                    if (service.getUser() != null && service.getUser().getEmail() != null) {
                        long durationSeconds = Duration.between(saved.getStartedAt(), saved.getResolvedAt()).getSeconds();
                        emailAlertService.sendIncidentResolvedAlert(
                                service.getUser().getEmail(),
                                service.getName(),
                                service.getUrl(),
                                durationSeconds,
                                saved.getResolvedAt()
                        );
                    }
                });
    }

    private void evictStatusCache(ServiceEntity service) {
        if (statusPageCacheService != null && service != null) {
            statusPageCacheService.evictForService(service);
        }
    }

    private boolean esUrlProtegida(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("google.com") || lower.contains("1.1.1.1") || lower.contains("wikipedia.org");
    }
}

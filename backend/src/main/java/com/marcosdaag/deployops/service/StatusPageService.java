package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.PublicIncidentDTO;
import com.marcosdaag.deployops.dto.PublicServiceDTO;
import com.marcosdaag.deployops.dto.StatusPageDTO;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.repository.IncidentRepository;
import com.marcosdaag.deployops.repository.ServiceRepository;
import com.marcosdaag.deployops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatusPageService {

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final MetricsService metricsService;
    private final StatusPageCacheService statusPageCacheService;

    @Transactional(readOnly = true)
    public StatusPageDTO getStatusPage(String slug) {
        String cleanSlug = slug != null ? slug.trim().toLowerCase() : "";
        String cacheSlug = cleanSlug.isEmpty() ? "demo" : cleanSlug;

        if (statusPageCacheService != null) {
            Optional<StatusPageDTO> cached = statusPageCacheService.get(cacheSlug);
            if (cached != null && cached.isPresent()) {
                return cached.get();
            }
        }

        UserEntity user;
        List<ServiceEntity> publicServices;
        String ownerName;

        if ("demo".equals(cleanSlug) || cleanSlug.isEmpty()) {
            ownerName = "DeployOps Global Demo";
            publicServices = serviceRepository.findByUserIsNullOrderByIdDesc();
        } else {
            user = userRepository.findByPublicSlug(cleanSlug)
                    .orElseThrow(() -> new ResourceNotFoundException("No se encontro una pagina de estado publica para el slug: " + cleanSlug));
            ownerName = user.getName();
            publicServices = serviceRepository.findByUserAndIsPublicStatusTrueOrderByIdDesc(user);
        }

        Map<Long, Double> uptimes = metricsService.calculateUptimesForServices(publicServices);

        List<PublicServiceDTO> serviceDTOs = publicServices.stream()
                .map(s -> PublicServiceDTO.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .urlMasked(maskUrl(s.getUrl()))
                        .status(s.getStatus())
                        .lastResponseTimeMs(s.getLastResponseTimeMs())
                        .lastCheckedAt(s.getLastCheckedAt())
                        .uptimePercentage(uptimes.getOrDefault(s.getId(), 100.0))
                        .build())
                .toList();

        List<PublicIncidentDTO> incidents = Collections.emptyList();
        if (!publicServices.isEmpty()) {
            List<IncidentEntity> incidentEntities = incidentRepository.findTop10ByServiceInOrderByStartedAtDesc(publicServices);
            incidents = incidentEntities.stream()
                    .map(inc -> PublicIncidentDTO.builder()
                            .id(inc.getId())
                            .serviceName(inc.getService().getName())
                            .startedAt(inc.getStartedAt())
                            .resolvedAt(inc.getResolvedAt())
                            .status(inc.getStatus())
                            .durationSeconds(inc.getResolvedAt() != null
                                    ? Duration.between(inc.getStartedAt(), inc.getResolvedAt()).getSeconds()
                                    : null)
                            .build())
                    .toList();
        }

        String overallStatus = computeOverallStatus(publicServices);
        double overallUptime = serviceDTOs.isEmpty()
                ? 100.0
                : serviceDTOs.stream().mapToDouble(PublicServiceDTO::getUptimePercentage).average().orElse(100.0);

        StatusPageDTO result = StatusPageDTO.builder()
                .slug(cleanSlug)
                .ownerName(ownerName)
                .overallStatus(overallStatus)
                .overallUptime(Math.round(overallUptime * 100.0) / 100.0)
                .services(serviceDTOs)
                .recentIncidents(incidents)
                .build();

        if (statusPageCacheService != null) {
            statusPageCacheService.put(cacheSlug, result);
        }

        return result;
    }

    @Transactional
    public void toggleServiceVisibility(Long serviceId, boolean isPublic, String currentUserEmail) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + serviceId));

        if (service.getUser() != null) {
            if (currentUserEmail == null || !service.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new SecurityException("No tienes permisos para modificar la visibilidad de este servicio.");
            }
        }

        service.setPublicStatus(isPublic);
        serviceRepository.save(service);

        if (statusPageCacheService != null) {
            statusPageCacheService.evictForService(service);
        }
    }

    @Transactional
    public String updateUserSlug(String newSlug, String currentUserEmail) {
        if (currentUserEmail == null || currentUserEmail.isBlank()) {
            throw new SecurityException("Debes haber iniciado sesion para modificar tu slug publico.");
        }

        UserEntity user = userRepository.findByEmail(currentUserEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado."));

        String sanitized = sanitizeSlug(newSlug);
        if (sanitized.length() < 3) {
            throw new IllegalArgumentException("El slug debe tener al menos 3 caracteres alfanumericos.");
        }

        if (sanitized.equalsIgnoreCase("demo")) {
            throw new IllegalArgumentException("El slug 'demo' esta reservado por el sistema.");
        }

        if (!sanitized.equalsIgnoreCase(user.getPublicSlug()) && userRepository.existsByPublicSlug(sanitized)) {
            throw new IllegalArgumentException("El slug publico ya se encuentra en uso. Por favor elige otro.");
        }

        String previousSlug = user.getPublicSlug();
        user.setPublicSlug(sanitized);
        userRepository.save(user);

        if (statusPageCacheService != null) {
            if (previousSlug != null && !previousSlug.isBlank()) {
                statusPageCacheService.evictBySlug(previousSlug);
            }
            statusPageCacheService.evictBySlug(sanitized);
        }

        return sanitized;
    }

    private String computeOverallStatus(List<ServiceEntity> services) {
        if (services.isEmpty()) {
            return "OPERATIONAL";
        }
        boolean hasDown = services.stream().anyMatch(s -> s.getStatus() == ServiceStatus.DOWN);
        if (hasDown) {
            return "MAJOR_OUTAGE";
        }
        boolean hasDegraded = services.stream().anyMatch(s -> s.getStatus() == ServiceStatus.DEGRADED || s.getStatus() == ServiceStatus.RECOVERING);
        if (hasDegraded) {
            return "DEGRADED";
        }
        return "OPERATIONAL";
    }

    private String maskUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);
            String host = uri.getHost();
            return host != null ? host : rawUrl;
        } catch (Exception e) {
            return rawUrl;
        }
    }

    private String sanitizeSlug(String slug) {
        if (slug == null) return "";
        return slug.trim().toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}

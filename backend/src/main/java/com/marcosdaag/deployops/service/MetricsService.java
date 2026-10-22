package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final IncidentRepository incidentRepository;

    // Calcula el porcentaje de Uptime de un servicio consultando sus incidentes individuales
    public double calculateUptimePercentage(ServiceEntity service) {
        if (service.getStatus() == ServiceStatus.PENDING && service.getLastCheckedAt() == null) {
            return 0.0;
        }

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime windowStart = (service.getCreatedAt() != null && service.getCreatedAt().isAfter(thirtyDaysAgo))
                ? service.getCreatedAt()
                : thirtyDaysAgo;

        List<IncidentEntity> incidents = incidentRepository.findOverlappingIncidents(service, windowStart);
        return calculateUptimeFromIncidents(service, incidents, windowStart);
    }

    // Calcula los uptimes de todos los servicios en una sola consulta grupal para evitar el problema N+1
    public Map<Long, Double> calculateUptimesForServices(List<ServiceEntity> services) {
        if (services.isEmpty()) {
            return Collections.emptyMap();
        }

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<IncidentEntity> allIncidents = incidentRepository.findOverlappingIncidentsForServices(services, thirtyDaysAgo);

        // Agrupamos los incidentes por el ID de cada servicio en memoria
        Map<Long, List<IncidentEntity>> incidentsByServiceId = allIncidents.stream()
                .filter(i -> i.getService() != null && i.getService().getId() != null)
                .collect(Collectors.groupingBy(i -> i.getService().getId()));

        Map<Long, Double> result = new HashMap<>();
        for (ServiceEntity service : services) {
            if (service.getStatus() == ServiceStatus.PENDING && service.getLastCheckedAt() == null) {
                result.put(service.getId(), 0.0);
                continue;
            }

            LocalDateTime windowStart = (service.getCreatedAt() != null && service.getCreatedAt().isAfter(thirtyDaysAgo))
                    ? service.getCreatedAt()
                    : thirtyDaysAgo;

            List<IncidentEntity> serviceIncidents = incidentsByServiceId.getOrDefault(service.getId(), Collections.emptyList());
            double uptime = calculateUptimeFromIncidents(service, serviceIncidents, windowStart);
            result.put(service.getId(), uptime);
        }

        return result;
    }

    // Metodo auxiliar para hacer la cuenta matematica del Uptime
    private double calculateUptimeFromIncidents(ServiceEntity service, List<IncidentEntity> incidents, LocalDateTime windowStart) {
        LocalDateTime now = LocalDateTime.now();
        long totalWindowSeconds = Duration.between(windowStart, now).toSeconds();

        if (totalWindowSeconds <= 0) {
            return service.getStatus() == ServiceStatus.DOWN ? 0.0 : 100.0;
        }

        long totalDowntimeSeconds = 0;

        for (IncidentEntity incident : incidents) {
            LocalDateTime effectiveStart = incident.getStartedAt().isBefore(windowStart)
                    ? windowStart
                    : incident.getStartedAt();

            LocalDateTime effectiveEnd = (incident.getResolvedAt() != null && incident.getResolvedAt().isBefore(now))
                    ? incident.getResolvedAt()
                    : now;

            if (effectiveEnd.isAfter(effectiveStart)) {
                totalDowntimeSeconds += Duration.between(effectiveStart, effectiveEnd).toSeconds();
            }
        }

        if (totalDowntimeSeconds >= totalWindowSeconds) {
            return 0.0;
        }

        // Formula: ((Total - Caida) / Total) * 100
        double uptime = ((double) (totalWindowSeconds - totalDowntimeSeconds) / totalWindowSeconds) * 100.0;
        double rounded = Math.round(uptime * 100.0) / 100.0;

        return Math.max(0.0, Math.min(100.0, rounded));
    }
}

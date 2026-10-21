package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final IncidentRepository incidentRepository;

    // Calcula el porcentaje de Uptime de los ultimos 30 dias
    public double calculateUptimePercentage(ServiceEntity service) {
        // Si todavia no se le hizo el primer ping, devolvemos 0%
        if (service.getStatus() == ServiceStatus.PENDING && service.getLastCheckedAt() == null) {
            return 0.0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        // Si el servicio se creo hace menos de 30 dias, calculamos desde su fecha de creacion
        LocalDateTime windowStart = (service.getCreatedAt() != null && service.getCreatedAt().isAfter(thirtyDaysAgo))
                ? service.getCreatedAt()
                : thirtyDaysAgo;

        long totalWindowSeconds = Duration.between(windowStart, now).toSeconds();

        if (totalWindowSeconds <= 0) {
            return service.getStatus() == ServiceStatus.DOWN ? 0.0 : 100.0;
        }

        // Buscamos los incidentes de este servicio dentro de la ventana de tiempo
        List<IncidentEntity> incidents = incidentRepository.findOverlappingIncidents(service, windowStart);

        long totalDowntimeSeconds = 0;

        for (IncidentEntity incident : incidents) {
            LocalDateTime effectiveStart = incident.getStartedAt().isBefore(windowStart)
                    ? windowStart
                    : incident.getStartedAt();

            // Si sigue ONGOING, calculamos hasta el momento actual
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

        // Formula de Uptime: ((Total - Caida) / Total) * 100
        double uptime = ((double) (totalWindowSeconds - totalDowntimeSeconds) / totalWindowSeconds) * 100.0;
        double rounded = Math.round(uptime * 100.0) / 100.0;

        return Math.max(0.0, Math.min(100.0, rounded));
    }
}

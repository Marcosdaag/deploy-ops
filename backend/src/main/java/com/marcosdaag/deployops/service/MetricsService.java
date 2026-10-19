package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
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

    /**
     * Calcula el porcentaje de Uptime (disponibilidad) de los últimos 30 días.
     * Si el servicio fue creado hace menos de 30 días, la ventana de cálculo
     * se ajusta a la antigüedad real del servicio para no distorsionar la métrica.
     */
    public double calculateUptimePercentage(ServiceEntity service) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        // Determinamos el inicio real de la ventana de monitoreo
        LocalDateTime windowStart = (service.getCreatedAt() != null && service.getCreatedAt().isAfter(thirtyDaysAgo))
                ? service.getCreatedAt()
                : thirtyDaysAgo;

        long totalWindowMinutes = Duration.between(windowStart, now).toMinutes();

        // Si el servicio se acaba de crear hace menos de 1 minuto
        if (totalWindowMinutes <= 0) {
            return 100.0;
        }

        // Consultamos directamente en base de datos los incidentes dentro de la ventana
        List<IncidentEntity> incidents = incidentRepository.findByServiceAndStartedAtAfter(service, windowStart);

        long totalDowntimeMinutes = 0;

        for (IncidentEntity incident : incidents) {
            LocalDateTime incidentStart = incident.getStartedAt().isBefore(windowStart)
                    ? windowStart
                    : incident.getStartedAt();

            LocalDateTime incidentEnd = (incident.getResolvedAt() != null)
                    ? incident.getResolvedAt()
                    : now;

            Duration duration = Duration.between(incidentStart, incidentEnd);
            totalDowntimeMinutes += duration.toMinutes();
        }

        if (totalDowntimeMinutes >= totalWindowMinutes) {
            return 0.0;
        }

        double uptime = ((double) (totalWindowMinutes - totalDowntimeMinutes) / totalWindowMinutes) * 100.0;
        double rounded = Math.round(uptime * 100.0) / 100.0;

        // Aseguramos que siempre esté acotado entre 0.0 y 100.0
        return Math.max(0.0, Math.min(100.0, rounded));
    }
}
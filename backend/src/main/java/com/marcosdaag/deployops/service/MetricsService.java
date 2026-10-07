package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MetricsService {

    @Autowired
    private IncidentRepository incidentRepository;

    // Calcula el porcentaje de Uptime (disponibilidad) de los últimos 30 días
    public double calculateUptimePercentage(ServiceEntity service) {

        // Asumimos una ventana de tiempo de 30 días
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long totalMinutesIn30Days = 30 * 24 * 60; // 43.200 minutos
        long totalDowntimeMinutes = 0;

        // Necesitamos buscar TODOS los incidentes de este servicio.
        List<IncidentEntity> incidents = incidentRepository.findByService(service);

        for (IncidentEntity incident : incidents) {

            // Si el incidente empezó hace más de 30 días, lo ignoramos para la métrica actual
            if (incident.getStartedAt().isBefore(thirtyDaysAgo)) {
                continue;
            }

            // Calculamos el final del incidente (si sigue ONGOING, calculamos hasta el minuto actual)
            LocalDateTime endTime = (incident.getResolvedAt() != null) ? incident.getResolvedAt() : LocalDateTime.now();

            // Calculamos cuántos minutos duró el apagón
            Duration duration = Duration.between(incident.getStartedAt(), endTime);
            totalDowntimeMinutes += duration.toMinutes();
        }

        // Si estuvo caído más tiempo que el total (ej: acabamos de crearlo y ya está caído), devolvemos 0%
        if (totalDowntimeMinutes >= totalMinutesIn30Days) {
            return 0.0;
        }

        // Fórmula de Uptime: ((Total - Caída) / Total) * 100
        double uptime = ((double) (totalMinutesIn30Days - totalDowntimeMinutes) / totalMinutesIn30Days) * 100;

        // Redondeamos a 2 decimales (ej: 99.99%)
        return Math.round(uptime * 100.0) / 100.0;
    }
}
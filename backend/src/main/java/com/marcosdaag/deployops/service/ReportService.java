package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.MonthlyReportDTO;
import com.marcosdaag.deployops.dto.PublicIncidentDTO;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.PingLogEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.repository.IncidentRepository;
import com.marcosdaag.deployops.repository.PingLogRepository;
import com.marcosdaag.deployops.repository.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final PingLogRepository pingLogRepository;
    private final EmailAlertService emailAlertService;

    @Transactional(readOnly = true)
    public MonthlyReportDTO generateMonthlyReport(Long serviceId, int year, int month, String currentUserEmail) {
        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + serviceId));

        validateOwnership(service, currentUserEmail);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = LocalDateTime.of(year, month, 1, 0, 0, 0);
        LocalDateTime monthEnd = monthStart.plusMonths(1).minusNanos(1);
        LocalDateTime effectiveEnd = monthEnd.isAfter(now) ? now : monthEnd;

        long totalMonitoredSeconds = Math.max(1L, Duration.between(monthStart, effectiveEnd).getSeconds());

        List<IncidentEntity> incidents = incidentRepository.findIncidentsInPeriod(service, monthStart, effectiveEnd);

        long totalDowntimeSeconds = 0L;
        for (IncidentEntity inc : incidents) {
            LocalDateTime incStart = inc.getStartedAt().isBefore(monthStart) ? monthStart : inc.getStartedAt();
            LocalDateTime incEnd = (inc.getResolvedAt() == null || inc.getResolvedAt().isAfter(effectiveEnd))
                    ? effectiveEnd
                    : inc.getResolvedAt();
            long duration = Math.max(0L, Duration.between(incStart, incEnd).getSeconds());
            totalDowntimeSeconds += duration;
        }

        double uptimePercentage = 100.0;
        if (totalMonitoredSeconds > 0) {
            double raw = ((totalMonitoredSeconds - totalDowntimeSeconds) * 100.0) / totalMonitoredSeconds;
            uptimePercentage = Math.round(Math.max(0.0, Math.min(100.0, raw)) * 100.0) / 100.0;
        }

        int totalIncidents = incidents.size();
        double mttrMinutes = totalIncidents > 0
                ? Math.round(((double) totalDowntimeSeconds / 60.0 / totalIncidents) * 10.0) / 10.0
                : 0.0;

        List<PingLogEntity> pingLogs = pingLogRepository.findByServiceAndTimestampBetweenOrderByTimestampAsc(service, monthStart, effectiveEnd);

        List<Long> aliveLatencies = pingLogs.stream()
                .filter(PingLogEntity::isAlive)
                .map(PingLogEntity::getResponseTimeMs)
                .sorted()
                .toList();

        long min = aliveLatencies.isEmpty() ? 0L : aliveLatencies.getFirst();
        long max = aliveLatencies.isEmpty() ? 0L : aliveLatencies.getLast();
        double avg = aliveLatencies.isEmpty() ? 0.0 : Math.round(aliveLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0) * 10.0) / 10.0;
        long p95 = 0L;
        if (!aliveLatencies.isEmpty()) {
            int p95Index = (int) Math.ceil(0.95 * aliveLatencies.size()) - 1;
            p95 = aliveLatencies.get(Math.max(0, Math.min(p95Index, aliveLatencies.size() - 1)));
        }

        String monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.of("es", "ES"));
        String periodLabel = Character.toUpperCase(monthName.charAt(0)) + monthName.substring(1) + " " + year;

        List<PublicIncidentDTO> incidentDTOs = incidents.stream()
                .map(inc -> PublicIncidentDTO.builder()
                        .id(inc.getId())
                        .serviceName(service.getName())
                        .startedAt(inc.getStartedAt())
                        .resolvedAt(inc.getResolvedAt())
                        .status(inc.getStatus())
                        .durationSeconds(inc.getResolvedAt() != null
                                ? Duration.between(inc.getStartedAt(), inc.getResolvedAt()).getSeconds()
                                : Duration.between(inc.getStartedAt(), effectiveEnd).getSeconds())
                        .build())
                .toList();

        return MonthlyReportDTO.builder()
                .serviceId(service.getId())
                .serviceName(service.getName())
                .serviceUrl(service.getUrl())
                .year(year)
                .month(month)
                .periodLabel(periodLabel)
                .uptimePercentage(uptimePercentage)
                .totalMonitoredSeconds(totalMonitoredSeconds)
                .totalDowntimeSeconds(totalDowntimeSeconds)
                .totalIncidents(totalIncidents)
                .mttrMinutes(mttrMinutes)
                .minLatencyMs(min)
                .maxLatencyMs(max)
                .avgLatencyMs(avg)
                .p95LatencyMs(p95)
                .incidents(incidentDTOs)
                .build();
    }

    @Transactional(readOnly = true)
    public String generateMonthlyReportCsv(Long serviceId, int year, int month, String currentUserEmail) {
        MonthlyReportDTO report = generateMonthlyReport(serviceId, year, month, currentUserEmail);

        StringBuilder sb = new StringBuilder();
        sb.append("# REPORTE DE CUMPLIMIENTO Y DISPONIBILIDAD (SLA)\n");
        sb.append("# Servicio: ").append(report.getServiceName()).append(" (").append(report.getServiceUrl()).append(")\n");
        sb.append("# Periodo: ").append(report.getPeriodLabel()).append("\n");
        sb.append("# SLA Uptime: ").append(report.getUptimePercentage()).append("%\n");
        sb.append("# Tiempo Inactivo Total: ").append(report.getTotalDowntimeSeconds() / 60).append(" minutos\n");
        sb.append("# Incidentes Totales: ").append(report.getTotalIncidents()).append("\n");
        sb.append("# MTTR Promedio: ").append(report.getMttrMinutes()).append(" minutos\n");
        sb.append("# Latencia Min/Avg/p95/Max: ")
                .append(report.getMinLatencyMs()).append("ms / ")
                .append(report.getAvgLatencyMs()).append("ms / ")
                .append(report.getP95LatencyMs()).append("ms / ")
                .append(report.getMaxLatencyMs()).append("ms\n#\n");

        sb.append("ID Incidente,Servicio,Inicio,Resolucion,Duracion Segundos,Estado\n");
        for (PublicIncidentDTO inc : report.getIncidents()) {
            sb.append(inc.getId()).append(",")
                    .append("\"").append(inc.getServiceName().replace("\"", "\"\"")).append("\",")
                    .append(inc.getStartedAt() != null ? inc.getStartedAt() : "").append(",")
                    .append(inc.getResolvedAt() != null ? inc.getResolvedAt() : "En curso").append(",")
                    .append(inc.getDurationSeconds() != null ? inc.getDurationSeconds() : 0).append(",")
                    .append(inc.getStatus()).append("\n");
        }

        return sb.toString();
    }

    @Transactional
    public void sendMonthlyReportEmail(Long serviceId, int year, int month, String currentUserEmail) {
        MonthlyReportDTO report = generateMonthlyReport(serviceId, year, month, currentUserEmail);

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontro el servicio con ID: " + serviceId));

        String recipientEmail = (currentUserEmail != null && !currentUserEmail.isBlank())
                ? currentUserEmail
                : (service.getUser() != null ? service.getUser().getEmail() : null);

        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("No hay una direccion de correo electronico asociada para enviar el reporte.");
        }

        emailAlertService.sendMonthlyReport(
                recipientEmail,
                report.getServiceName(),
                report.getServiceUrl(),
                report.getPeriodLabel(),
                report.getUptimePercentage(),
                report.getTotalDowntimeSeconds(),
                report.getTotalIncidents(),
                report.getMttrMinutes()
        );
    }

    private void validateOwnership(ServiceEntity service, String currentUserEmail) {
        if (service.getUser() != null) {
            if (currentUserEmail == null || !service.getUser().getEmail().equalsIgnoreCase(currentUserEmail)) {
                throw new SecurityException("No tienes permisos para acceder a los reportes de este servicio.");
            }
        }
    }
}

package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.MonthlyReportDTO;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.PingLogEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.enums.IncidentStatus;
import com.marcosdaag.deployops.repository.IncidentRepository;
import com.marcosdaag.deployops.repository.PingLogRepository;
import com.marcosdaag.deployops.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private PingLogRepository pingLogRepository;

    @Mock
    private EmailAlertService emailAlertService;

    @InjectMocks
    private ReportService reportService;

    private ServiceEntity service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setEmail("admin@example.com");
        user.setName("Admin Test");

        service = new ServiceEntity();
        service.setId(10L);
        service.setName("Gateway API");
        service.setUrl("https://api.example.com");
        service.setUser(user);
    }

    @Test
    @DisplayName("Genera reporte mensual sin incidentes con 100% SLA")
    void whenGenerateReport_noIncidents_shouldReturnFullUptime() {
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));
        when(incidentRepository.findIncidentsInPeriod(eq(service), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        PingLogEntity log1 = new PingLogEntity();
        log1.setService(service);
        log1.setResponseTimeMs(50L);
        log1.setAlive(true);
        log1.setTimestamp(LocalDateTime.now());

        PingLogEntity log2 = new PingLogEntity();
        log2.setService(service);
        log2.setResponseTimeMs(100L);
        log2.setAlive(true);
        log2.setTimestamp(LocalDateTime.now());

        when(pingLogRepository.findByServiceAndTimestampBetweenOrderByTimestampAsc(eq(service), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(log1, log2));

        MonthlyReportDTO report = reportService.generateMonthlyReport(10L, 2026, 11, "admin@example.com");

        assertNotNull(report);
        assertEquals(10L, report.getServiceId());
        assertEquals("Gateway API", report.getServiceName());
        assertEquals(100.0, report.getUptimePercentage());
        assertEquals(0, report.getTotalIncidents());
        assertEquals(0L, report.getTotalDowntimeSeconds());
        assertEquals(50L, report.getMinLatencyMs());
        assertEquals(100L, report.getMaxLatencyMs());
        assertEquals(75.0, report.getAvgLatencyMs());
    }

    @Test
    @DisplayName("Calcula downtime y MTTR correctamente ante incidentes")
    void whenGenerateReport_withIncidents_shouldComputeDowntimeAndMttr() {
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));

        LocalDateTime start = LocalDateTime.of(2026, 11, 2, 10, 0, 0);
        LocalDateTime resolved = LocalDateTime.of(2026, 11, 2, 10, 30, 0);

        IncidentEntity incident = new IncidentEntity();
        incident.setId(100L);
        incident.setService(service);
        incident.setStartedAt(start);
        incident.setResolvedAt(resolved);
        incident.setStatus(IncidentStatus.RESOLVED);

        when(incidentRepository.findIncidentsInPeriod(eq(service), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(incident));
        when(pingLogRepository.findByServiceAndTimestampBetweenOrderByTimestampAsc(eq(service), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        MonthlyReportDTO report = reportService.generateMonthlyReport(10L, 2026, 11, "admin@example.com");

        assertNotNull(report);
        assertEquals(1, report.getTotalIncidents());
        assertEquals(1800L, report.getTotalDowntimeSeconds());
        assertEquals(30.0, report.getMttrMinutes());
        assertTrue(report.getUptimePercentage() < 100.0);
    }

    @Test
    @DisplayName("Lanza SecurityException si el usuario no es dueno del servicio")
    void whenGenerateReport_unauthorizedUser_shouldThrowSecurityException() {
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));

        assertThrows(SecurityException.class, () ->
                reportService.generateMonthlyReport(10L, 2026, 11, "intruder@example.com")
        );
    }

    @Test
    @DisplayName("Lanza ResourceNotFoundException si el servicio no existe")
    void whenGenerateReport_notFound_shouldThrowException() {
        when(serviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                reportService.generateMonthlyReport(99L, 2026, 11, "admin@example.com")
        );
    }

    @Test
    @DisplayName("Genera CSV con cabeceras y resumen ejecutivo")
    void whenGenerateReportCsv_shouldContainExecutiveSummary() {
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));
        when(incidentRepository.findIncidentsInPeriod(eq(service), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(pingLogRepository.findByServiceAndTimestampBetweenOrderByTimestampAsc(eq(service), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        String csv = reportService.generateMonthlyReportCsv(10L, 2026, 11, "admin@example.com");

        assertNotNull(csv);
        assertTrue(csv.contains("# REPORTE DE CUMPLIMIENTO Y DISPONIBILIDAD (SLA)"));
        assertTrue(csv.contains("Gateway API"));
        assertTrue(csv.contains("ID Incidente,Servicio,Inicio,Resolucion,Duracion Segundos,Estado"));
    }

    @Test
    @DisplayName("Envia reporte mensual por email exitosamente")
    void whenSendMonthlyReportEmail_shouldTriggerEmailAlertService() {
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));
        when(incidentRepository.findIncidentsInPeriod(eq(service), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(pingLogRepository.findByServiceAndTimestampBetweenOrderByTimestampAsc(eq(service), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        reportService.sendMonthlyReportEmail(10L, 2026, 11, "admin@example.com");

        verify(emailAlertService).sendMonthlyReport(
                eq("admin@example.com"),
                eq("Gateway API"),
                eq("https://api.example.com"),
                anyString(),
                eq(100.0),
                eq(0L),
                eq(0),
                eq(0.0)
        );
    }

    @Test
    @DisplayName("Lanza IllegalArgumentException si no hay email destinatario")
    void whenSendMonthlyReportEmail_noEmail_shouldThrowException() {
        service.setUser(null);
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(service));
        when(incidentRepository.findIncidentsInPeriod(eq(service), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(pingLogRepository.findByServiceAndTimestampBetweenOrderByTimestampAsc(eq(service), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        assertThrows(IllegalArgumentException.class, () ->
                reportService.sendMonthlyReportEmail(10L, 2026, 11, null)
        );
    }
}

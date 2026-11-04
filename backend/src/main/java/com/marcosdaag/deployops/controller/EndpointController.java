package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.dto.EndpointCreateDTO;
import com.marcosdaag.deployops.dto.EndpointResponseDTO;
import com.marcosdaag.deployops.dto.IncidentResponseDTO;
import com.marcosdaag.deployops.dto.MonthlyReportDTO;
import com.marcosdaag.deployops.dto.ServiceMetricsHistoryDTO;
import com.marcosdaag.deployops.dto.VisibilityUpdateRequest;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.service.EndpointManagerService;
import com.marcosdaag.deployops.service.MetricsHistoryService;
import com.marcosdaag.deployops.service.ReportService;
import com.marcosdaag.deployops.service.SseNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/endpoints")
@RequiredArgsConstructor
public class EndpointController {

    private final EndpointManagerService endpointManager;
    private final MetricsHistoryService metricsHistoryService;
    private final SseNotificationService sseNotificationService;
    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<List<EndpointResponseDTO>> getEndpoints(
            @RequestParam(required = false) String scope,
            Authentication authentication
    ) {
        String email = getEmailFromAuth(authentication);
        return ResponseEntity.ok(endpointManager.getDashboardData(email, scope));
    }

    @PostMapping("/samples")
    public ResponseEntity<List<EndpointResponseDTO>> seedSampleEndpoints(Authentication authentication) {
        String email = getEmailFromAuth(authentication);
        return ResponseEntity.ok(endpointManager.seedSampleEndpointsForUser(email));
    }

    @PostMapping
    public ResponseEntity<EndpointResponseDTO> createEndpoint(
            @Valid @RequestBody EndpointCreateDTO dto,
            Authentication authentication
    ) {
        String email = getEmailFromAuth(authentication);
        ServiceEntity created = endpointManager.createEndpoint(dto, email);
        EndpointResponseDTO response = new EndpointResponseDTO(created, 0.0, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEndpoint(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String email = getEmailFromAuth(authentication);
        endpointManager.deleteEndpoint(id, email);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/incidents")
    public ResponseEntity<List<IncidentResponseDTO>> getIncidents(@PathVariable Long id) {
        return ResponseEntity.ok(endpointManager.getIncidentsForService(id));
    }

    @GetMapping("/{id}/metrics")
    public ResponseEntity<ServiceMetricsHistoryDTO> getMetrics(
            @PathVariable Long id,
            @RequestParam(defaultValue = "24h") String range,
            Authentication authentication
    ) {
        String email = getEmailFromAuth(authentication);
        return ResponseEntity.ok(metricsHistoryService.getMetricsHistory(id, range, email));
    }

    @PatchMapping("/{id}/visibility")
    public ResponseEntity<Void> toggleVisibility(
            @PathVariable Long id,
            @RequestBody VisibilityUpdateRequest request,
            Authentication authentication
    ) {
        String email = getEmailFromAuth(authentication);
        endpointManager.toggleVisibility(id, request.isPublic(), email);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/ping")
    public ResponseEntity<EndpointResponseDTO> triggerManualPing(@PathVariable Long id) {
        return ResponseEntity.ok(endpointManager.triggerManualPing(id));
    }

    @PostMapping("/{id}/test-alert")
    public ResponseEntity<Void> triggerTestAlert(@PathVariable Long id, Authentication authentication) {
        String email = getEmailFromAuth(authentication);
        endpointManager.triggerTestAlert(id, email);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<MonthlyReportDTO> getMonthlyReport(
            @PathVariable Long id,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Authentication authentication
    ) {
        int targetYear = (year != null && year > 2000) ? year : LocalDate.now().getYear();
        int targetMonth = (month != null && month >= 1 && month <= 12) ? month : LocalDate.now().getMonthValue();
        String email = getEmailFromAuth(authentication);
        return ResponseEntity.ok(reportService.generateMonthlyReport(id, targetYear, targetMonth, email));
    }

    @GetMapping("/{id}/report/csv")
    public ResponseEntity<String> downloadMonthlyReportCsv(
            @PathVariable Long id,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Authentication authentication
    ) {
        int targetYear = (year != null && year > 2000) ? year : LocalDate.now().getYear();
        int targetMonth = (month != null && month >= 1 && month <= 12) ? month : LocalDate.now().getMonthValue();
        String email = getEmailFromAuth(authentication);
        String csvContent = reportService.generateMonthlyReportCsv(id, targetYear, targetMonth, email);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-sla-servicio-" + id + "-" + targetYear + "-" + String.format("%02d", targetMonth) + ".csv\"");

        return ResponseEntity.ok().headers(headers).body(csvContent);
    }

    @PostMapping("/{id}/report/email")
    public ResponseEntity<Void> sendMonthlyReportEmail(
            @PathVariable Long id,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Authentication authentication
    ) {
        int targetYear = (year != null && year > 2000) ? year : LocalDate.now().getYear();
        int targetMonth = (month != null && month >= 1 && month <= 12) ? month : LocalDate.now().getMonthValue();
        String email = getEmailFromAuth(authentication);
        reportService.sendMonthlyReportEmail(id, targetYear, targetMonth, email);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDashboard() {
        return sseNotificationService.createEmitter();
    }

    private String getEmailFromAuth(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return null;
    }
}

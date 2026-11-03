package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.dto.EndpointCreateDTO;
import com.marcosdaag.deployops.dto.EndpointResponseDTO;
import com.marcosdaag.deployops.dto.IncidentResponseDTO;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.service.EndpointManagerService;
import com.marcosdaag.deployops.service.SseNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/endpoints")
@RequiredArgsConstructor
public class EndpointController {

    private final EndpointManagerService endpointManager;
    private final SseNotificationService sseNotificationService;

    @GetMapping
    public ResponseEntity<List<EndpointResponseDTO>> getEndpoints(Authentication authentication) {
        String email = getEmailFromAuth(authentication);
        return ResponseEntity.ok(endpointManager.getDashboardData(email));
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

    @PostMapping("/{id}/ping")
    public ResponseEntity<EndpointResponseDTO> triggerManualPing(@PathVariable Long id) {
        return ResponseEntity.ok(endpointManager.triggerManualPing(id));
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

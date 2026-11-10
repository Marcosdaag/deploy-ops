package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.dto.StatusPageDTO;
import com.marcosdaag.deployops.service.StatusPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST para la visualizacion y configuracion de Status Pages publicas.
 * Permite la consulta publica de la disponibilidad y estado de servicios marcados como visibles,
 * respaldada por la capa de cache distribuida L2 en Redis, asi como la personalizacion del slug de acceso.
 */
@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusPageController {

    private final StatusPageService statusPageService;

    @GetMapping("/{slug}")
    public ResponseEntity<StatusPageDTO> getStatusPage(@PathVariable String slug) {
        return ResponseEntity.ok(statusPageService.getStatusPage(slug));
    }

    @PatchMapping("/slug")
    public ResponseEntity<Map<String, String>> updateUserSlug(
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : null;
        String newSlug = body.get("slug");
        String updated = statusPageService.updateUserSlug(newSlug, email);
        return ResponseEntity.ok(Map.of("slug", updated));
    }
}

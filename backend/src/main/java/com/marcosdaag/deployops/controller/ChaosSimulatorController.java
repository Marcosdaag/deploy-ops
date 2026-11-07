package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.dto.ChaosStateDTO;
import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.repository.UserRepository;
import com.marcosdaag.deployops.service.ChaosSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chaos")
@RequiredArgsConstructor
public class ChaosSimulatorController {

    private final ChaosSimulationService chaosSimulationService;
    private final UserRepository userRepository;

    @GetMapping("/state")
    public ResponseEntity<ChaosStateDTO> getChaosState(Authentication authentication) {
        Long userId = getUserId(authentication);
        int simulatedCode = chaosSimulationService.getSimulatedStatusCode(userId);
        boolean active = simulatedCode != 200;
        String desc = active ? "Simulando degradacion HTTP " + simulatedCode : "Sandbox operando normalmente (HTTP 200)";

        return ResponseEntity.ok(new ChaosStateDTO(simulatedCode, active, desc));
    }

    @PostMapping("/state")
    public ResponseEntity<ChaosStateDTO> updateChaosState(
            @RequestBody Map<String, Integer> request,
            Authentication authentication
    ) {
        Long userId = getUserId(authentication);
        int code = request.getOrDefault("statusCode", 200);
        chaosSimulationService.setSimulatedStatusCode(userId, code);

        boolean active = code != 200;
        String desc = active ? "Simulando degradacion HTTP " + code : "Sandbox operando normalmente (HTTP 200)";
        return ResponseEntity.ok(new ChaosStateDTO(code, active, desc));
    }

    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerChaos(
            @RequestBody(required = false) Map<String, String> request,
            Authentication authentication
    ) {
        Long userId = getUserId(authentication);
        String desc = (request != null && request.containsKey("description"))
                ? request.get("description")
                : "Disparo manual de Caos SRE";

        OutboxEventEntity event = chaosSimulationService.triggerChaosEvent(userId, desc);
        return ResponseEntity.ok(Map.of(
                "eventId", event.getId(),
                "eventType", event.getEventType().name(),
                "status", event.getStatus().name(),
                "simulatedStatusCode", chaosSimulationService.getSimulatedStatusCode(userId)
        ));
    }

    private Long getUserId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            UserEntity user = userRepository.findByEmail(authentication.getName().toLowerCase()).orElse(null);
            if (user != null) {
                return user.getId();
            }
        }
        return 0L; // Usuario anonimo / showcase
    }
}

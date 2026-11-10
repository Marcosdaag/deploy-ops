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

/**
 * Controlador REST para el simulador de Caos e inyeccion de fallos SRE.
 * Permite simular respuestas HTTP degradadas (500, 503, 504, 401) y disparar eventos
 * controlados para verificar en tiempo real la resiliencia del pipeline de entrega,
 * las politicas de reintento con backoff exponencial y el enrutamiento a Dead Letter Queue.
 */
@RestController
@RequestMapping("/api/chaos")
@RequiredArgsConstructor
public class ChaosSimulatorController {

    private final ChaosSimulationService chaosSimulationService;
    private final UserRepository userRepository;

    /**
     * Consulta el estado de simulacion actual para el operador autenticado o sesion demo.
     *
     * @param authentication contexto de seguridad activo.
     * @return DTO con el codigo HTTP simulado, indicador de actividad y descripcion.
     */
    @GetMapping("/state")
    public ResponseEntity<ChaosStateDTO> getChaosState(Authentication authentication) {
        Long userId = getUserId(authentication);
        int simulatedCode = chaosSimulationService.getSimulatedStatusCode(userId);
        boolean active = simulatedCode != 200;
        String desc = active ? "Simulando degradacion HTTP " + simulatedCode : "Sandbox operando normalmente (HTTP 200)";

        return ResponseEntity.ok(new ChaosStateDTO(simulatedCode, active, desc));
    }

    /**
     * Actualiza el codigo de respuesta HTTP simulado en memoria para las pruebas de contingencia.
     *
     * @param request mapa con el nuevo codigo HTTP ('statusCode').
     * @param authentication contexto de seguridad activo.
     * @return DTO con el estado de simulacion actualizado.
     */
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

    /**
     * Dispara manualmente un evento de caos para probar el flujo de entrega y desvio a DLQ.
     *
     * @param request mapa opcional con descripcion personalizada del ensayo.
     * @param authentication contexto de seguridad activo.
     * @return mapa con metadatos del evento generado y codigo HTTP simulado asociado.
     */
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

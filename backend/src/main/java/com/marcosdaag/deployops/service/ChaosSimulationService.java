package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.enums.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ChaosSimulationService {

    private static final Logger logger = LoggerFactory.getLogger(ChaosSimulationService.class);

    private final OutboxEventService outboxEventService;

    // Estado virtual particionado en memoria por userId
    private final ConcurrentHashMap<Long, Integer> userChaosStatusCode = new ConcurrentHashMap<>();

    public int getSimulatedStatusCode(Long userId) {
        if (userId == null) {
            return 200;
        }
        return userChaosStatusCode.getOrDefault(userId, 200);
    }

    public void setSimulatedStatusCode(Long userId, int statusCode) {
        if (userId == null) {
            return;
        }
        userChaosStatusCode.put(userId, statusCode);
        logger.info("Estado de caos simulado actualizado para usuario ID {}: HTTP {}", userId, statusCode);
    }

    public OutboxEventEntity triggerChaosEvent(Long userId, String description) {
        int simulatedCode = getSimulatedStatusCode(userId);

        Map<String, Object> details = Map.of(
                "simulation", true,
                "description", description != null ? description : "Inyeccion de Caos SRE",
                "simulatedStatusCode", simulatedCode,
                "strategy", "BACKOFF_JITTER_3X"
        );

        logger.info("Disparando evento de caos para usuario {} con respuesta esperada {}", userId, simulatedCode);
        return outboxEventService.recordManualEvent(userId, OutboxEventType.CHAOS_SIMULATION, details);
    }
}

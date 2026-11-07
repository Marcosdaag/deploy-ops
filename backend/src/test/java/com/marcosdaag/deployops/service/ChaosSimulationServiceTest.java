package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.model.enums.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChaosSimulationServiceTest {

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private ChaosSimulationService chaosSimulationService;

    @Test
    @DisplayName("Estado de caos por defecto es 200 y puede ser actualizado por usuario")
    void shouldManageChaosStatePerUser() {
        Long userId = 10L;
        assertEquals(200, chaosSimulationService.getSimulatedStatusCode(userId));

        chaosSimulationService.setSimulatedStatusCode(userId, 503);
        assertEquals(503, chaosSimulationService.getSimulatedStatusCode(userId));

        // Otro usuario permanece en 200
        assertEquals(200, chaosSimulationService.getSimulatedStatusCode(99L));
    }

    @Test
    @DisplayName("Disparo de caos registra evento manual en Outbox")
    void shouldTriggerChaosEvent() {
        Long userId = 10L;
        chaosSimulationService.setSimulatedStatusCode(userId, 504);

        OutboxEventEntity mockEvent = new OutboxEventEntity();
        mockEvent.setId("chaos-evt-1");
        mockEvent.setEventType(OutboxEventType.CHAOS_SIMULATION);
        mockEvent.setStatus(OutboxStatus.PENDING);

        when(outboxEventService.recordManualEvent(eq(userId), eq(OutboxEventType.CHAOS_SIMULATION), any(Map.class)))
                .thenReturn(mockEvent);

        OutboxEventEntity result = chaosSimulationService.triggerChaosEvent(userId, "Prueba Gateway Timeout");

        assertNotNull(result);
        assertEquals(OutboxEventType.CHAOS_SIMULATION, result.getEventType());
        verify(outboxEventService).recordManualEvent(eq(userId), eq(OutboxEventType.CHAOS_SIMULATION), any(Map.class));
    }
}

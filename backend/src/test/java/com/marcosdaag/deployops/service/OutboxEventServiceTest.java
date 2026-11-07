package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.enums.IncidentStatus;
import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.model.enums.OutboxStatus;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxEventService outboxEventService;

    private ServiceEntity service;
    private IncidentEntity incident;

    @BeforeEach
    void setUp() {
        UserEntity user = new UserEntity();
        user.setId(42L);

        service = new ServiceEntity();
        service.setId(10L);
        service.setName("Core API");
        service.setUrl("https://api.empresa.com/health");
        service.setStatus(ServiceStatus.DOWN);
        service.setExpectedStatusCode(200);
        service.setUser(user);

        incident = new IncidentEntity();
        incident.setId(100L);
        incident.setService(service);
        incident.setStatus(IncidentStatus.ONGOING);
        incident.setStartedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Registra evento de incidente en la tabla Outbox con estado PENDING")
    void shouldRecordIncidentEventSuccessfully() {
        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OutboxEventEntity event = outboxEventService.recordIncidentEvent(
                service,
                incident,
                OutboxEventType.INCIDENT_OPENED
        );

        assertNotNull(event);
        assertNotNull(event.getId());
        assertEquals("SERVICE", event.getAggregateType());
        assertEquals(10L, event.getAggregateId());
        assertEquals(42L, event.getUserId());
        assertEquals(OutboxEventType.INCIDENT_OPENED, event.getEventType());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertTrue(event.getPayloadJson().contains("Core API"));

        verify(outboxEventRepository, times(1)).save(any(OutboxEventEntity.class));
    }

    @Test
    @DisplayName("Registra evento manual/caos correctamente")
    void shouldRecordManualEventSuccessfully() {
        when(outboxEventRepository.save(any(OutboxEventEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OutboxEventEntity event = outboxEventService.recordManualEvent(
                42L,
                OutboxEventType.CHAOS_SIMULATION,
                Map.of("simulatedStatusCode", 503)
        );

        assertNotNull(event);
        assertEquals(OutboxEventType.CHAOS_SIMULATION, event.getEventType());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertTrue(event.getPayloadJson().contains("503"));
    }
}

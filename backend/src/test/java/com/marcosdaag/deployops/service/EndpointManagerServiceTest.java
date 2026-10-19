package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.enums.IncidentStatus;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.repository.IncidentRepository;
import com.marcosdaag.deployops.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndpointManagerServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private EndpointManagerService endpointManagerService;

    private ServiceEntity service;

    @BeforeEach
    void setUp() {
        service = new ServiceEntity();
        service.setId(1L);
        service.setName("API Pagos");
        service.setUrl("https://api.pagos.com/health");
        service.setStatus(ServiceStatus.HEALTHY);
        service.setConsecutiveFailures(0);
        service.setConsecutiveSuccesses(0);
    }

    @Test
    @DisplayName("1er fallo: Transiciona de HEALTHY a DEGRADED sin abrir incidente (Tolerancia a fallos)")
    void whenPingFailsOnce_shouldTransitionToDegradedWithoutOpeningIncident() {
        endpointManagerService.processPingResult(service, false);

        assertEquals(ServiceStatus.DEGRADED, service.getStatus());
        assertEquals(1, service.getConsecutiveFailures());
        assertEquals(0, service.getConsecutiveSuccesses());
        assertNotNull(service.getLastCheckedAt());

        verify(serviceRepository).save(service);
        verify(incidentRepository, never()).save(any(IncidentEntity.class));
    }

    @Test
    @DisplayName("2do fallo consecutivo: Permanece en DEGRADED sin abrir incidente")
    void whenPingFailsTwiceConsecutively_shouldRemainDegraded() {
        service.setStatus(ServiceStatus.DEGRADED);
        service.setConsecutiveFailures(1);

        endpointManagerService.processPingResult(service, false);

        assertEquals(ServiceStatus.DEGRADED, service.getStatus());
        assertEquals(2, service.getConsecutiveFailures());
        verify(incidentRepository, never()).save(any(IncidentEntity.class));
    }

    @Test
    @DisplayName("3er fallo consecutivo: Transiciona a DOWN y abre un nuevo incidente ONGOING")
    void whenPingFailsThreeTimes_shouldTransitionToDownAndOpenIncident() {
        service.setStatus(ServiceStatus.DEGRADED);
        service.setConsecutiveFailures(2);

        endpointManagerService.processPingResult(service, false);

        assertEquals(ServiceStatus.DOWN, service.getStatus());
        assertEquals(3, service.getConsecutiveFailures());

        ArgumentCaptor<IncidentEntity> incidentCaptor = ArgumentCaptor.forClass(IncidentEntity.class);
        verify(incidentRepository).save(incidentCaptor.capture());

        IncidentEntity savedIncident = incidentCaptor.getValue();
        assertEquals(IncidentStatus.ONGOING, savedIncident.getStatus());
        assertEquals(service, savedIncident.getService());
        assertNotNull(savedIncident.getStartedAt());
        assertNull(savedIncident.getResolvedAt());
    }

    @Test
    @DisplayName("Falsa alarma: Un éxito mientras está en DEGRADED regresa inmediatamente a HEALTHY")
    void whenDegradedServiceSucceeds_shouldRecoverDirectlyToHealthy() {
        service.setStatus(ServiceStatus.DEGRADED);
        service.setConsecutiveFailures(2);

        endpointManagerService.processPingResult(service, true);

        assertEquals(ServiceStatus.HEALTHY, service.getStatus());
        assertEquals(0, service.getConsecutiveFailures());
        assertEquals(1, service.getConsecutiveSuccesses());
        verify(incidentRepository, never()).save(any(IncidentEntity.class));
    }

    @Test
    @DisplayName("1er éxito tras caída: Transiciona de DOWN a RECOVERING manteniendo el incidente abierto")
    void whenDownServiceSucceedsOnce_shouldTransitionToRecovering() {
        service.setStatus(ServiceStatus.DOWN);
        service.setConsecutiveFailures(3);

        endpointManagerService.processPingResult(service, true);

        assertEquals(ServiceStatus.RECOVERING, service.getStatus());
        assertEquals(0, service.getConsecutiveFailures());
        assertEquals(1, service.getConsecutiveSuccesses());
        verify(incidentRepository, never()).save(any(IncidentEntity.class));
    }

    @Test
    @DisplayName("2do éxito consecutivo tras caída: Transiciona a HEALTHY y cierra el incidente activo (RESOLVED)")
    void whenRecoveringServiceSucceedsSecondTime_shouldTransitionToHealthyAndCloseIncident() {
        service.setStatus(ServiceStatus.RECOVERING);
        service.setConsecutiveSuccesses(1);

        IncidentEntity ongoingIncident = new IncidentEntity();
        ongoingIncident.setId(10L);
        ongoingIncident.setService(service);
        ongoingIncident.setStatus(IncidentStatus.ONGOING);
        ongoingIncident.setStartedAt(LocalDateTime.now().minusMinutes(5));

        when(incidentRepository.findByServiceAndStatus(service, IncidentStatus.ONGOING))
                .thenReturn(Optional.of(ongoingIncident));

        endpointManagerService.processPingResult(service, true);

        assertEquals(ServiceStatus.HEALTHY, service.getStatus());
        assertEquals(2, service.getConsecutiveSuccesses());

        verify(incidentRepository).save(ongoingIncident);
        assertEquals(IncidentStatus.RESOLVED, ongoingIncident.getStatus());
        assertNotNull(ongoingIncident.getResolvedAt());
    }

    @Test
    @DisplayName("Recaída: Un fallo durante el estado RECOVERING devuelve inmediatamente a DOWN")
    void whenRecoveringServiceFails_shouldRelapseToDown() {
        service.setStatus(ServiceStatus.RECOVERING);
        service.setConsecutiveSuccesses(1);

        endpointManagerService.processPingResult(service, false);

        assertEquals(ServiceStatus.DOWN, service.getStatus());
        assertEquals(1, service.getConsecutiveFailures());
        assertEquals(0, service.getConsecutiveSuccesses());
    }
}

package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.EndpointCreateDTO;
import com.marcosdaag.deployops.dto.EndpointResponseDTO;
import com.marcosdaag.deployops.dto.IncidentResponseDTO;
import com.marcosdaag.deployops.dto.PingResult;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
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

    @Mock
    private HttpMonitorService httpMonitorService;

    @Mock
    private SseNotificationService sseNotificationService;

    @Spy
    private UrlSecurityValidator urlSecurityValidator = new UrlSecurityValidator();

    @InjectMocks
    private EndpointManagerService endpointManagerService;

    private ServiceEntity service;

    private final PingResult successPing = PingResult.success(200, 120L);
    private final PingResult failedPing = PingResult.failure(500, 300L, "Internal Server Error");

    @BeforeEach
    void setUp() {
        service = new ServiceEntity();
        service.setId(1L);
        service.setName("API Pagos");
        service.setUrl("https://api.pagos.com/health");
        service.setStatus(ServiceStatus.HEALTHY);
        service.setConsecutiveFailures(0);
        service.setConsecutiveSuccesses(0);
        service.setProtected(false);
    }

    @Test
    @DisplayName("Crea endpoint correctamente y queda en PENDING")
    void whenCreatingValidEndpoint_shouldSaveWithPendingStatus() {
        EndpointCreateDTO dto = new EndpointCreateDTO();
        dto.setName("GitHub API");
        dto.setUrl("https://api.github.com");

        when(serviceRepository.existsByUrl("https://api.github.com")).thenReturn(false);
        when(serviceRepository.save(any(ServiceEntity.class))).thenAnswer(i -> {
            ServiceEntity s = i.getArgument(0);
            s.setId(10L);
            return s;
        });

        ServiceEntity result = endpointManagerService.createEndpoint(dto);

        assertNotNull(result);
        assertEquals("GitHub API", result.getName());
        assertEquals("https://api.github.com", result.getUrl());
        assertEquals(ServiceStatus.PENDING, result.getStatus());
        assertEquals(0, result.getConsecutiveFailures());
        assertFalse(result.isProtected());
        verify(serviceRepository).save(any(ServiceEntity.class));
        verify(sseNotificationService).broadcastDashboardUpdate(any());
    }

    @Test
    @DisplayName("Falla al crear endpoint con URL insegura o duplicada")
    void whenCreatingInvalidEndpoint_shouldThrowException() {
        EndpointCreateDTO dtoSsrf = new EndpointCreateDTO();
        dtoSsrf.setName("Admin Interno");
        dtoSsrf.setUrl("http://localhost:8080/actuator");

        assertThrows(IllegalArgumentException.class, () -> endpointManagerService.createEndpoint(dtoSsrf));

        EndpointCreateDTO dtoDup = new EndpointCreateDTO();
        dtoDup.setName("GitHub");
        dtoDup.setUrl("https://api.github.com");

        when(serviceRepository.existsByUrl("https://api.github.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> endpointManagerService.createEndpoint(dtoDup));
    }

    @Test
    @DisplayName("Primer fallo en servicio HEALTHY: pasa a DEGRADED sin abrir incidente")
    void whenHealthyServiceFailsOnce_shouldTransitionToDegradedWithoutIncident() {
        when(serviceRepository.existsById(1L)).thenReturn(true);

        endpointManagerService.processPingResult(service, failedPing);

        assertEquals(ServiceStatus.DEGRADED, service.getStatus());
        assertEquals(1, service.getConsecutiveFailures());
        assertEquals(0, service.getConsecutiveSuccesses());
        assertEquals(300L, service.getLastResponseTimeMs());
        verify(incidentRepository, never()).save(any(IncidentEntity.class));
    }

    @Test
    @DisplayName("Segundo fallo consecutivo: se mantiene en DEGRADED")
    void whenDegradedServiceFailsAgain_shouldStayDegraded() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        service.setStatus(ServiceStatus.DEGRADED);
        service.setConsecutiveFailures(1);

        endpointManagerService.processPingResult(service, failedPing);

        assertEquals(ServiceStatus.DEGRADED, service.getStatus());
        assertEquals(2, service.getConsecutiveFailures());
        verify(incidentRepository, never()).save(any(IncidentEntity.class));
    }

    @Test
    @DisplayName("Tercer fallo consecutivo: pasa a DOWN y abre incidente")
    void whenDegradedServiceFailsThirdTime_shouldTransitionToDownAndOpenIncident() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        service.setStatus(ServiceStatus.DEGRADED);
        service.setConsecutiveFailures(2);

        when(incidentRepository.findByServiceAndStatus(service, IncidentStatus.ONGOING))
                .thenReturn(Optional.empty());

        endpointManagerService.processPingResult(service, failedPing);

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
    @DisplayName("Falsa alarma: si esta en DEGRADED y responde bien, vuelve a HEALTHY")
    void whenDegradedServiceSucceeds_shouldRecoverDirectlyToHealthy() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        service.setStatus(ServiceStatus.DEGRADED);
        service.setConsecutiveFailures(2);

        endpointManagerService.processPingResult(service, successPing);

        assertEquals(ServiceStatus.HEALTHY, service.getStatus());
        assertEquals(0, service.getConsecutiveFailures());
        assertEquals(1, service.getConsecutiveSuccesses());
        assertEquals(120L, service.getLastResponseTimeMs());
        verify(incidentRepository, never()).save(any(IncidentEntity.class));
    }

    @Test
    @DisplayName("1er exito tras caida: pasa de DOWN a RECOVERING")
    void whenDownServiceSucceedsOnce_shouldTransitionToRecovering() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        service.setStatus(ServiceStatus.DOWN);
        service.setConsecutiveFailures(3);

        endpointManagerService.processPingResult(service, successPing);

        assertEquals(ServiceStatus.RECOVERING, service.getStatus());
        assertEquals(0, service.getConsecutiveFailures());
        assertEquals(1, service.getConsecutiveSuccesses());
        verify(incidentRepository, never()).save(any(IncidentEntity.class));
    }

    @Test
    @DisplayName("2do exito seguido tras caida: vuelve a HEALTHY y cierra el incidente")
    void whenRecoveringServiceSucceedsSecondTime_shouldTransitionToHealthyAndCloseIncident() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        service.setStatus(ServiceStatus.RECOVERING);
        service.setConsecutiveSuccesses(1);

        IncidentEntity ongoingIncident = new IncidentEntity();
        ongoingIncident.setId(10L);
        ongoingIncident.setService(service);
        ongoingIncident.setStatus(IncidentStatus.ONGOING);
        ongoingIncident.setStartedAt(LocalDateTime.now().minusMinutes(5));

        when(incidentRepository.findByServiceAndStatus(service, IncidentStatus.ONGOING))
                .thenReturn(Optional.of(ongoingIncident));

        endpointManagerService.processPingResult(service, successPing);

        assertEquals(ServiceStatus.HEALTHY, service.getStatus());
        assertEquals(2, service.getConsecutiveSuccesses());

        verify(incidentRepository).save(ongoingIncident);
        assertEquals(IncidentStatus.RESOLVED, ongoingIncident.getStatus());
        assertNotNull(ongoingIncident.getResolvedAt());
    }

    @Test
    @DisplayName("Recaida: si falla estando en RECOVERING vuelve a DOWN")
    void whenRecoveringServiceFails_shouldRelapseToDown() {
        when(serviceRepository.existsById(1L)).thenReturn(true);
        service.setStatus(ServiceStatus.RECOVERING);
        service.setConsecutiveSuccesses(1);

        endpointManagerService.processPingResult(service, failedPing);

        assertEquals(ServiceStatus.DOWN, service.getStatus());
        assertEquals(1, service.getConsecutiveFailures());
        assertEquals(0, service.getConsecutiveSuccesses());
    }

    @Test
    @DisplayName("Borrado: elimina primero los incidentes y luego el servicio, o tira 404 si no existe")
    void whenDeletingEndpoint_shouldDeleteIncidentsFirstOrThrow404() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));

        endpointManagerService.deleteEndpoint(1L);

        verify(incidentRepository).deleteByServiceId(1L);
        verify(serviceRepository).deleteById(1L);
        verify(sseNotificationService).broadcastDashboardUpdate(any());

        when(serviceRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> endpointManagerService.deleteEndpoint(999L));
    }

    @Test
    @DisplayName("Proteccion de borrado: rechaza eliminar endpoints marcados como protegidos")
    void whenDeletingProtectedEndpoint_shouldThrowIllegalArgumentException() {
        service.setProtected(true);
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));

        assertThrows(IllegalArgumentException.class, () -> endpointManagerService.deleteEndpoint(1L));
        verify(serviceRepository, never()).deleteById(anyLong());
        verify(incidentRepository, never()).deleteByServiceId(anyLong());
    }

    @Test
    @DisplayName("Historial de incidentes: devuelve lista ordenada de incidentes con duracion")
    void whenGettingIncidentsForService_shouldReturnOrderedDTOs() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));

        IncidentEntity inc1 = new IncidentEntity();
        inc1.setId(101L);
        inc1.setService(service);
        inc1.setStatus(IncidentStatus.RESOLVED);
        inc1.setStartedAt(LocalDateTime.now().minusHours(2));
        inc1.setResolvedAt(LocalDateTime.now().minusHours(1));

        IncidentEntity inc2 = new IncidentEntity();
        inc2.setId(102L);
        inc2.setService(service);
        inc2.setStatus(IncidentStatus.ONGOING);
        inc2.setStartedAt(LocalDateTime.now().minusMinutes(10));

        when(incidentRepository.findByServiceOrderByStartedAtDesc(service)).thenReturn(List.of(inc2, inc1));

        List<IncidentResponseDTO> result = endpointManagerService.getIncidentsForService(1L);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(102L, result.get(0).getId());
        assertEquals(IncidentStatus.ONGOING, result.get(0).getStatus());
        assertEquals(101L, result.get(1).getId());
        assertEquals(IncidentStatus.RESOLVED, result.get(1).getStatus());
        assertTrue(result.get(1).getDurationSeconds() >= 3590); // ~1 hora = 3600s
    }

    @Test
    @DisplayName("Ping manual: ejecuta ping inmediato, actualiza estado y notifica por SSE")
    void whenTriggeringManualPing_shouldPingAndReturnResponseDTO() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(serviceRepository.existsById(1L)).thenReturn(true);
        when(httpMonitorService.ping("https://api.pagos.com/health")).thenReturn(successPing);
        when(metricsService.calculateUptimePercentage(service)).thenReturn(99.9);

        EndpointResponseDTO response = endpointManagerService.triggerManualPing(1L);

        assertNotNull(response);
        assertEquals("API Pagos", response.getName());
        assertEquals(99.9, response.getUptimePercentage());
        assertEquals(120L, response.getLastResponseTimeMs());
        verify(httpMonitorService).ping("https://api.pagos.com/health");
        verify(sseNotificationService, atLeastOnce()).broadcastDashboardUpdate(any());
    }

    @Test
    @DisplayName("Condicion de carrera: si el servicio fue borrado durante el ping, se descarta el guardado")
    void whenServiceWasDeletedDuringPing_shouldNotSave() {
        when(serviceRepository.existsById(1L)).thenReturn(false);

        endpointManagerService.processPingResult(service, successPing);

        verify(serviceRepository, never()).save(any(ServiceEntity.class));
    }
}

package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.StatusPageDTO;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.enums.IncidentStatus;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.repository.IncidentRepository;
import com.marcosdaag.deployops.repository.ServiceRepository;
import com.marcosdaag.deployops.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusPageServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private StatusPageService statusPageService;

    private UserEntity user;
    private ServiceEntity publicService;

    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(10L);
        user.setName("Marcos Dev");
        user.setEmail("marcos@example.com");
        user.setPublicSlug("marcos-dev");

        publicService = new ServiceEntity();
        publicService.setId(1L);
        publicService.setName("API Gateway");
        publicService.setUrl("https://api.gateway.example.com/health");
        publicService.setStatus(ServiceStatus.HEALTHY);
        publicService.setPublicStatus(true);
        publicService.setUser(user);
    }

    @Test
    @DisplayName("Obtiene status page publica para un slug valido con URL enmascarada e incidentes")
    void whenGetStatusPageForUserSlug_shouldReturnPublicData() {
        when(userRepository.findByPublicSlug("marcos-dev")).thenReturn(Optional.of(user));
        when(serviceRepository.findByUserAndIsPublicStatusTrueOrderByIdDesc(user)).thenReturn(List.of(publicService));
        when(metricsService.calculateUptimesForServices(any())).thenReturn(Map.of(1L, 99.9));

        IncidentEntity incident = new IncidentEntity();
        incident.setId(100L);
        incident.setService(publicService);
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setStartedAt(LocalDateTime.now().minusHours(2));
        incident.setResolvedAt(LocalDateTime.now().minusHours(1));

        when(incidentRepository.findTop10ByServiceInOrderByStartedAtDesc(any())).thenReturn(List.of(incident));

        StatusPageDTO result = statusPageService.getStatusPage("marcos-dev");

        assertNotNull(result);
        assertEquals("marcos-dev", result.getSlug());
        assertEquals("Marcos Dev", result.getOwnerName());
        assertEquals("OPERATIONAL", result.getOverallStatus());
        assertEquals(99.9, result.getOverallUptime());
        assertEquals(1, result.getServices().size());
        assertEquals("api.gateway.example.com", result.getServices().getFirst().getUrlMasked());
        assertEquals(1, result.getRecentIncidents().size());
        assertEquals(3600L, result.getRecentIncidents().getFirst().getDurationSeconds());
    }

    @Test
    @DisplayName("Obtiene status page demo cuando el slug es 'demo'")
    void whenGetStatusPageDemo_shouldReturnGlobalShowcase() {
        when(serviceRepository.findByUserIsNullOrderByIdDesc()).thenReturn(List.of(publicService));
        when(metricsService.calculateUptimesForServices(any())).thenReturn(Map.of(1L, 100.0));

        StatusPageDTO result = statusPageService.getStatusPage("demo");

        assertNotNull(result);
        assertEquals("DeployOps Global Demo", result.getOwnerName());
        assertEquals("OPERATIONAL", result.getOverallStatus());
    }

    @Test
    @DisplayName("Lanza ResourceNotFoundException si el slug no existe")
    void whenSlugNotFound_shouldThrowException() {
        when(userRepository.findByPublicSlug("inexistente")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> statusPageService.getStatusPage("inexistente"));
    }

    @Test
    @DisplayName("Permite alternar visibilidad de servicio al propietario")
    void whenToggleServiceVisibilityByOwner_shouldSave() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(publicService));

        statusPageService.toggleServiceVisibility(1L, false, "marcos@example.com");

        assertFalse(publicService.isPublicStatus());
        verify(serviceRepository).save(publicService);
    }

    @Test
    @DisplayName("Rechaza alternar visibilidad si el usuario no es el dueno")
    void whenToggleServiceVisibilityByNonOwner_shouldThrowSecurityException() {
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(publicService));

        assertThrows(SecurityException.class, () -> statusPageService.toggleServiceVisibility(1L, false, "hacker@example.com"));
        verify(serviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Actualiza el slug publico si es valido y esta disponible")
    void whenUpdateUserSlug_shouldSanitizeAndSave() {
        when(userRepository.findByEmail("marcos@example.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByPublicSlug("nuevo-slug")).thenReturn(false);

        String result = statusPageService.updateUserSlug(" Nuevo Slug! ", "marcos@example.com");

        assertEquals("nuevo-slug", result);
        assertEquals("nuevo-slug", user.getPublicSlug());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Rechaza actualizacion de slug si ya esta en uso o es 'demo'")
    void whenUpdateUserSlugConflict_shouldThrowException() {
        when(userRepository.findByEmail("marcos@example.com")).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> statusPageService.updateUserSlug("demo", "marcos@example.com"));

        when(userRepository.existsByPublicSlug("ocupado")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> statusPageService.updateUserSlug("ocupado", "marcos@example.com"));
    }
}

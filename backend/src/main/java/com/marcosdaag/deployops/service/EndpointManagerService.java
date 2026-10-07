package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.EndpointCreateDTO;
import com.marcosdaag.deployops.dto.EndpointResponseDTO;
import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.repository.IncidentRepository;
import com.marcosdaag.deployops.repository.ServiceRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EndpointManagerService {
    // Inyecciones
    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private MetricsService metricsService;

    // Crea un "servicio" o endpoint a vigilar
    public ServiceEntity createEndpoint(@NonNull EndpointCreateDTO dto) {
        ServiceEntity newEndpoint = new ServiceEntity();
        newEndpoint.setName(dto.getName());
        newEndpoint.setUrl(dto.getUrl());
        newEndpoint.setStatus("UP"); // Por defecto lo seteamos en UP

        return serviceRepository.save(newEndpoint);
    }

    public void deleteEndpoint(Long id) {
        serviceRepository.deleteById(id);
    }

    // Lista todos los endpoints a vigilar
    public List<ServiceEntity> getAllEndpoints() {
        return serviceRepository.findAll();
    }

    public List<EndpointResponseDTO> getDashboardData() {
        List<ServiceEntity> services = serviceRepository.findAll();
        return services.stream().map(service -> {
            double uptime = metricsService.calculateUptimePercentage(service);
            return new EndpointResponseDTO(service, uptime);
        }).collect(java.util.stream.Collectors.toList());
    }

    // Evalúa qué hacer con el resultado del pingeo y gestiona incidentes
    public void processPingResult(ServiceEntity service, boolean isAlive) {

        if (isAlive && "DOWN".equals(service.getStatus())) {
            // ESTABA CAÍDO Y REVIVIÓ
            service.setStatus("UP");
            serviceRepository.save(service);

            // Buscamos el incidente abierto y lo cerramos
            IncidentEntity incident = incidentRepository.findByServiceAndStatus(service, "ONGOING");
            if (incident != null) {
                incident.setResolvedAt(LocalDateTime.now());
                incident.setStatus("RESOLVED");
                incidentRepository.save(incident);
            }

        } else if (!isAlive && "UP".equals(service.getStatus())) {
            // ESTABA VIVO Y SE CAYÓ
            service.setStatus("DOWN");
            serviceRepository.save(service);

            // Registramos un nuevo incidente en la base de datos
            IncidentEntity newIncident = new IncidentEntity();
            newIncident.setService(service);
            newIncident.setStatus("ONGOING");
            newIncident.setStartedAt(LocalDateTime.now());
            incidentRepository.save(newIncident);
        }
    }
}
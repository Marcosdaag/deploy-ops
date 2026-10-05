package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EndpointManagerService {

    @Autowired
    private ServiceRepository serviceRepository; // Inyeccion del REPOSITORIO JPA

    // Creamos un "servicio" o endpoint a vigilar
    public ServiceEntity createEndpoint(String name, String url) {
        ServiceEntity newEndpoint = new ServiceEntity();
        newEndpoint.setName(name);
        newEndpoint.setUrl(url);
        newEndpoint.setStatus("UP"); // Por defecto lo seteamos en UP

        // Mediante JPA guardamos nuestro nuevo servicio
        return serviceRepository.save(newEndpoint);
    }

    // Metodo JPA para listar todos los endpoints
    public List<ServiceEntity> getAllEndpoints() {
        return serviceRepository.findAll();
    }
}
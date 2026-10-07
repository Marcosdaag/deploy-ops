package com.marcosdaag.deployops.dto;

import com.marcosdaag.deployops.model.ServiceEntity;
import lombok.Getter;

@Getter
public class EndpointResponseDTO {
    private Long id;
    private String name;
    private String url;
    private String status;
    private double uptimePercentage; // La metrica

    // Constructor que convierte una Entidad en un DTO
    public EndpointResponseDTO(ServiceEntity entity, double uptimePercentage) {
        this.id = entity.getId();
        this.name = entity.getName();
        this.url = entity.getUrl();
        this.status = entity.getStatus();
        this.uptimePercentage = uptimePercentage;
    }
}
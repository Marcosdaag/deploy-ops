package com.marcosdaag.deployops.dto;

import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class EndpointResponseDTO {
    private final Long id;
    private final String name;
    private final String url;
    private final ServiceStatus status;
    private final int consecutiveFailures;
    private final LocalDateTime lastCheckedAt;
    private final LocalDateTime createdAt;
    private final double uptimePercentage;

    public EndpointResponseDTO(ServiceEntity entity, double uptimePercentage) {
        this.id = entity.getId();
        this.name = entity.getName();
        this.url = entity.getUrl();
        this.status = entity.getStatus();
        this.consecutiveFailures = entity.getConsecutiveFailures();
        this.lastCheckedAt = entity.getLastCheckedAt();
        this.createdAt = entity.getCreatedAt();
        this.uptimePercentage = uptimePercentage;
    }
}
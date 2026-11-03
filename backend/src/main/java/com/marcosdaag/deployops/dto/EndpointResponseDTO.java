package com.marcosdaag.deployops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private final Long lastResponseTimeMs;
    private final LocalDateTime lastCheckedAt;
    private final LocalDateTime createdAt;
    private final double uptimePercentage;

    @JsonProperty("isProtected")
    private final boolean isProtected;

    @JsonProperty("isOwner")
    private final boolean isOwner;

    private final String ownerEmail;

    @JsonProperty("isPublicStatus")
    private final boolean isPublicStatus;
    private final String httpMethod;
    private final Integer expectedStatusCode;
    private final String customHeaders;
    private final Integer sslDaysRemaining;
    private final LocalDateTime sslExpiryDate;
    private final String sslIssuer;

    public EndpointResponseDTO(ServiceEntity entity, double uptimePercentage) {
        this(entity, uptimePercentage, null);
    }

    public EndpointResponseDTO(ServiceEntity entity, double uptimePercentage, String currentUserEmail) {
        this.id = entity.getId();
        this.name = entity.getName();
        this.url = entity.getUrl();
        this.status = entity.getStatus();
        this.consecutiveFailures = entity.getConsecutiveFailures();
        this.lastResponseTimeMs = entity.getLastResponseTimeMs();
        this.lastCheckedAt = entity.getLastCheckedAt();
        this.createdAt = entity.getCreatedAt();
        this.uptimePercentage = uptimePercentage;
        this.isProtected = entity.isProtected();
        this.isPublicStatus = entity.isPublicStatus();
        this.httpMethod = entity.getHttpMethod() != null ? entity.getHttpMethod() : "GET";
        this.expectedStatusCode = entity.getExpectedStatusCode();
        this.customHeaders = entity.getCustomHeaders();
        this.sslDaysRemaining = entity.getSslDaysRemaining();
        this.sslExpiryDate = entity.getSslExpiryDate();
        this.sslIssuer = entity.getSslIssuer();
        this.ownerEmail = entity.getUser() != null ? entity.getUser().getEmail() : null;
        this.isOwner = entity.getUser() != null
                && currentUserEmail != null
                && entity.getUser().getEmail().equalsIgnoreCase(currentUserEmail);
    }
}

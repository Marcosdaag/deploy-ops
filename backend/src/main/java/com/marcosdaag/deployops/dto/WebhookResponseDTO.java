package com.marcosdaag.deployops.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.marcosdaag.deployops.model.WebhookEndpointEntity;
import com.marcosdaag.deployops.model.enums.WebhookProviderType;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookResponseDTO {

    private Long id;
    private String name;
    private String targetUrl;
    private WebhookProviderType providerType;
    private String secretHint;
    private String rawSecretOnce; // Solo visible en alta o rotacion inmediata
    private boolean enabled;
    private String lastDeliveryStatus;
    private LocalDateTime createdAt;

    public WebhookResponseDTO() {}

    public WebhookResponseDTO(WebhookEndpointEntity entity, String rawSecretOnce) {
        if (entity != null) {
            this.id = entity.getId();
            this.name = entity.getName();
            this.targetUrl = entity.getTargetUrl();
            this.providerType = entity.getProviderType();
            this.secretHint = entity.getSecretHint();
            this.rawSecretOnce = rawSecretOnce;
            this.enabled = entity.isEnabled();
            this.lastDeliveryStatus = entity.getLastDeliveryStatus();
            this.createdAt = entity.getCreatedAt();
        }
    }
}

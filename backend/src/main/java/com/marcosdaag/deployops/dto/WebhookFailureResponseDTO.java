package com.marcosdaag.deployops.dto;

import com.marcosdaag.deployops.model.WebhookDeliveryFailureEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class WebhookFailureResponseDTO {

    private Long id;
    private Long webhookId;
    private String webhookName;
    private String targetUrl;
    private String eventId;
    private String eventType;
    private Integer httpStatus;
    private String errorDetail;
    private int attemptCount;
    private String payloadSnapshot;
    private String status;
    private LocalDateTime failedAt;
    private LocalDateTime resolvedAt;

    public WebhookFailureResponseDTO() {}

    public WebhookFailureResponseDTO(WebhookDeliveryFailureEntity entity) {
        if (entity != null) {
            this.id = entity.getId();
            this.webhookId = entity.getWebhook() != null ? entity.getWebhook().getId() : null;
            this.webhookName = entity.getWebhook() != null ? entity.getWebhook().getName() : "Eliminado";
            this.targetUrl = entity.getWebhook() != null ? entity.getWebhook().getTargetUrl() : "N/A";
            this.eventId = entity.getEventId();
            this.eventType = entity.getEventType() != null ? entity.getEventType().name() : null;
            this.httpStatus = entity.getHttpStatus();
            this.errorDetail = entity.getErrorDetail();
            this.attemptCount = entity.getAttemptCount();
            this.payloadSnapshot = entity.getPayloadSnapshot();
            this.status = entity.getStatus() != null ? entity.getStatus().name() : null;
            this.failedAt = entity.getFailedAt();
            this.resolvedAt = entity.getResolvedAt();
        }
    }
}

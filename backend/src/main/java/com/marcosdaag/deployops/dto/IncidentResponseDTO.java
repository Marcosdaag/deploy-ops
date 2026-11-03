package com.marcosdaag.deployops.dto;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.enums.IncidentStatus;
import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
public class IncidentResponseDTO {
    private final Long id;
    private final Long serviceId;
    private final IncidentStatus status;
    private final LocalDateTime startedAt;
    private final LocalDateTime resolvedAt;
    private final long durationSeconds;

    public IncidentResponseDTO(IncidentEntity entity) {
        this.id = entity.getId();
        this.serviceId = entity.getService() != null ? entity.getService().getId() : null;
        this.status = entity.getStatus();
        this.startedAt = entity.getStartedAt();
        this.resolvedAt = entity.getResolvedAt();

        LocalDateTime end = entity.getResolvedAt() != null ? entity.getResolvedAt() : LocalDateTime.now();
        this.durationSeconds = entity.getStartedAt() != null
                ? Math.max(0, Duration.between(entity.getStartedAt(), end).getSeconds())
                : 0L;
    }
}

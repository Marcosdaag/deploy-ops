package com.marcosdaag.deployops.dto;

import com.marcosdaag.deployops.model.enums.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicIncidentDTO {
    private Long id;
    private String serviceName;
    private LocalDateTime startedAt;
    private LocalDateTime resolvedAt;
    private IncidentStatus status;
    private Long durationSeconds;
}

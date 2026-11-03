package com.marcosdaag.deployops.dto;

import com.marcosdaag.deployops.model.enums.ServiceStatus;
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
public class PublicServiceDTO {
    private Long id;
    private String name;
    private String urlMasked;
    private ServiceStatus status;
    private Long lastResponseTimeMs;
    private LocalDateTime lastCheckedAt;
    private double uptimePercentage;
}

package com.marcosdaag.deployops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatusPageDTO {
    private String slug;
    private String ownerName;
    private String overallStatus;
    private double overallUptime;
    private List<PublicServiceDTO> services;
    private List<PublicIncidentDTO> recentIncidents;
}

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
public class MonthlyReportDTO {
    private Long serviceId;
    private String serviceName;
    private String serviceUrl;
    private int year;
    private int month;
    private String periodLabel;
    private double uptimePercentage;
    private long totalMonitoredSeconds;
    private long totalDowntimeSeconds;
    private int totalIncidents;
    private double mttrMinutes;
    private long minLatencyMs;
    private long maxLatencyMs;
    private double avgLatencyMs;
    private long p95LatencyMs;
    private List<PublicIncidentDTO> incidents;
}

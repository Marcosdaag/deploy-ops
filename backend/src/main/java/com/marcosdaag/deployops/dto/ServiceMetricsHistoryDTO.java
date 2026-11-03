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
public class ServiceMetricsHistoryDTO {
    private Long serviceId;
    private String serviceName;
    private String range;
    private long minLatencyMs;
    private long maxLatencyMs;
    private double avgLatencyMs;
    private long p95LatencyMs;
    private double uptimePercentage;
    private int totalPings;
    private List<PingPointDTO> points;
}

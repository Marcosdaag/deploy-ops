package com.marcosdaag.deployops.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DistributedOverviewDTO {
    private long outboxPendingCount;
    private long outboxProcessingCount;
    private long outboxProcessedCount;
    private long outboxFailedCount;
    private long dlqFailureCount;
    private long cacheL2Hits;
    private long cacheL2Misses;
    private long cacheL2Evictions;
    private boolean redisEnabled;
    private boolean rabbitmqEnabled;
}

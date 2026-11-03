package com.marcosdaag.deployops.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PingPointDTO {
    private long timestampMs;
    private long latencyMs;
    private int statusCode;
    private boolean alive;
}

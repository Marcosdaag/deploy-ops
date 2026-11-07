package com.marcosdaag.deployops.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChaosStateDTO {
    private int simulatedStatusCode;
    private boolean active;
    private String description;
}

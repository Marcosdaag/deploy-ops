package com.marcosdaag.deployops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VisibilityUpdateRequest {
    @JsonProperty("isPublic")
    private boolean isPublic;
}

package com.marcosdaag.deployops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleLoginRequest {

    @NotBlank(message = "El token de credencial de Google es obligatorio.")
    private String credential;
}

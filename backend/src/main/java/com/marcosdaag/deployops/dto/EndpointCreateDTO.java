package com.marcosdaag.deployops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Setter
@Getter
public class EndpointCreateDTO {
    @NotBlank(message = "El nombre del servicio no puede estar vacío.")
    private String name;

    @NotBlank(message = "La URL es obligatoria.")
    @URL(message = "Debe ser una URL válida que empiece con http o https.")
    private String url;
}
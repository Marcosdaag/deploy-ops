package com.marcosdaag.deployops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Setter
@Getter
public class EndpointCreateDTO {

    @NotBlank(message = "El nombre del servicio no puede estar vacio.")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
    private String name;

    @NotBlank(message = "La URL es obligatoria.")
    @Size(max = 500, message = "La URL no puede superar los 500 caracteres.")
    @URL(message = "Debe ser una URL valida que empiece con http o https.")
    private String url;

    private String httpMethod = "GET";

    private Integer expectedStatusCode;

    @Size(max = 1000, message = "Los headers personalizados no pueden superar los 1000 caracteres.")
    private String customHeaders;

    private boolean isPublicStatus = false;
}

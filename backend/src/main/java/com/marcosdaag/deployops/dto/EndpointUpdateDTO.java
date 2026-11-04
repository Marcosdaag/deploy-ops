package com.marcosdaag.deployops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
public class EndpointUpdateDTO {

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

    @JsonProperty("isPublicStatus")
    private boolean isPublicStatus = false;
}

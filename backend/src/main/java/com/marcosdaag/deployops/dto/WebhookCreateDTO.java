package com.marcosdaag.deployops.dto;

import com.marcosdaag.deployops.model.enums.WebhookProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebhookCreateDTO {

    @NotBlank(message = "El nombre del webhook es obligatorio.")
    @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres.")
    private String name;

    @NotBlank(message = "La URL de destino es obligatoria.")
    @Size(max = 500, message = "La URL no puede exceder los 500 caracteres.")
    private String targetUrl;

    private WebhookProviderType providerType = WebhookProviderType.CUSTOM;
}

package com.marcosdaag.deployops.dto;

import com.marcosdaag.deployops.model.enums.WebhookProviderType;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WebhookUpdateDTO {

    @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres.")
    private String name;

    @Size(max = 500, message = "La URL no puede exceder los 500 caracteres.")
    private String targetUrl;

    private WebhookProviderType providerType;

    private Boolean enabled;
}

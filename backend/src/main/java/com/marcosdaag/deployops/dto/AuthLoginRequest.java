package com.marcosdaag.deployops.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthLoginRequest {

    @NotBlank(message = "El email es obligatorio.")
    @Email(message = "Debe ser un email valido.")
    private String email;

    @NotBlank(message = "La contrasena es obligatoria.")
    private String password;
}

package com.marcosdaag.deployops.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthRegisterRequest {

    @NotBlank(message = "El nombre es obligatorio.")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
    private String name;

    @NotBlank(message = "El email es obligatorio.")
    @Email(message = "Debe ser un email valido.")
    @Size(max = 150, message = "El email no puede superar los 150 caracteres.")
    private String email;

    @NotBlank(message = "La contrasena es obligatoria.")
    @Size(min = 6, max = 100, message = "La contrasena debe tener entre 6 y 100 caracteres.")
    private String password;
}

package com.marcosdaag.deployops.controller;

import com.marcosdaag.deployops.dto.AuthLoginRequest;
import com.marcosdaag.deployops.dto.AuthRegisterRequest;
import com.marcosdaag.deployops.dto.AuthResponse;
import com.marcosdaag.deployops.dto.GoogleLoginRequest;
import com.marcosdaag.deployops.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para gestion de autenticacion, credenciales y sesiones.
 * Soporta registro tradicional con contrasenas en BCrypt, autenticacion OAuth2 con Google,
 * emision de tokens JWT sin estado y acceso instantaneo en modo Demo SRE.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Registra un nuevo usuario en la plataforma.
     *
     * @param request datos de registro (nombre, email y contrasena).
     * @return respuesta de autenticacion con token JWT y datos del usuario.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Autentica a un usuario con credenciales locales.
     *
     * @param request email y contrasena.
     * @return token JWT firmado y perfil del usuario.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Autentica a un usuario mediante ID Token de Google OAuth2 validado criptograficamente.
     *
     * @param request objeto contenedor con el idToken de Google.
     * @return token JWT propio de la plataforma y perfil.
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.loginWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Inicia una sesion inmediata con el usuario de demostracion preconfigurado.
     *
     * @return respuesta de autenticacion para la cuenta demo SRE.
     */
    @PostMapping("/demo")
    public ResponseEntity<AuthResponse> loginDemo() {
        AuthResponse response = authService.loginAsDemo();
        return ResponseEntity.ok(response);
    }

    /**
     * Retorna el perfil y los identificadores publicos del operador actualmente autenticado.
     *
     * @param authentication contexto de seguridad activo.
     * @return perfil del usuario autenticado o HTTP 401 si no hay sesion valida.
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.getCurrentUserResponse(authentication.getName()));
    }
}

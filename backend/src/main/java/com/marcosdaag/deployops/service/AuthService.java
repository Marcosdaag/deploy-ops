package com.marcosdaag.deployops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcosdaag.deployops.dto.AuthLoginRequest;
import com.marcosdaag.deployops.dto.AuthRegisterRequest;
import com.marcosdaag.deployops.dto.AuthResponse;
import com.marcosdaag.deployops.dto.GoogleLoginRequest;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.enums.AuthProvider;
import com.marcosdaag.deployops.model.enums.UserRole;
import com.marcosdaag.deployops.repository.UserRepository;
import com.marcosdaag.deployops.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public AuthResponse register(AuthRegisterRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("Ya existe una cuenta registrada con este correo electronico.");
        }

        UserEntity user = new UserEntity();
        user.setName(request.getName().trim());
        user.setEmail(cleanEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.ROLE_USER);
        user.setProvider(AuthProvider.LOCAL);
        user.setCreatedAt(LocalDateTime.now());
        user.setPublicSlug(generateUniqueSlug(user.getName()));

        UserEntity saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);
        return new AuthResponse(token, saved);
    }

    public AuthResponse login(AuthLoginRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        UserEntity user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas."));

        if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Credenciales invalidas.");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        try {
            String[] parts = request.getCredential().split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Token de credencial de Google invalido.");
            }

            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));

            String email = payload.path("email").asText(null);
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("No se encontro un correo valido en la cuenta de Google.");
            }

            email = email.trim().toLowerCase();
            String name = payload.path("name").asText(email.split("@")[0]);

            UserEntity user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                user = new UserEntity();
                user.setName(name);
                user.setEmail(email);
                user.setProvider(AuthProvider.GOOGLE);
                user.setRole(UserRole.ROLE_USER);
                user.setCreatedAt(LocalDateTime.now());
                user.setPublicSlug(generateUniqueSlug(user.getName()));
                user = userRepository.save(user);
            } else if (user.getPublicSlug() == null) {
                user.setPublicSlug(generateUniqueSlug(user.getName()));
                user = userRepository.save(user);
            }

            String token = jwtService.generateToken(user);
            return new AuthResponse(token, user);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al procesar la autenticacion con Google: " + e.getMessage());
        }
    }

    public String generateUniqueSlug(String name) {
        String base = (name == null || name.isBlank())
                ? "dev"
                : name.toLowerCase().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        if (base.isBlank()) {
            base = "dev";
        }
        String candidate = base;
        int counter = 1;
        while (userRepository.existsByPublicSlug(candidate)) {
            candidate = base + "-" + counter;
            counter++;
        }
        return candidate;
    }

    public AuthResponse getCurrentUserResponse(String email) {
        UserEntity user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user);
    }

    public UserEntity getUserByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }
}

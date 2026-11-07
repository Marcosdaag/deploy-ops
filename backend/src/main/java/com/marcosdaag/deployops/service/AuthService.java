package com.marcosdaag.deployops.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcosdaag.deployops.dto.AuthLoginRequest;
import com.marcosdaag.deployops.dto.AuthRegisterRequest;
import com.marcosdaag.deployops.dto.AuthResponse;
import com.marcosdaag.deployops.dto.GoogleLoginRequest;
import com.marcosdaag.deployops.exception.ResourceNotFoundException;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.enums.AuthProvider;
import com.marcosdaag.deployops.model.enums.ServiceStatus;
import com.marcosdaag.deployops.model.enums.UserRole;
import com.marcosdaag.deployops.repository.ServiceRepository;
import com.marcosdaag.deployops.repository.UserRepository;
import com.marcosdaag.deployops.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String GOOGLE_TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}";

    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate googleVerifierRestTemplate = buildVerifierRestTemplate();

    @Value("${app.google.client-id:}")
    private String googleClientId;

    private static RestTemplate buildVerifierRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(4));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

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

    @Transactional
    public AuthResponse login(AuthLoginRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        UserEntity user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas."));

        if (user.getPassword() == null) {
            throw new BadCredentialsException("Esta cuenta fue registrada con Google. Por favor, inicia sesion con Google.");
        } else if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Credenciales invalidas.");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        JsonNode payload = verifyGoogleIdToken(request.getCredential());

        String email = payload.path("email").asText(null);
        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("No se encontro un correo verificado en la cuenta de Google.");
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
    }

    JsonNode verifyGoogleIdToken(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new BadCredentialsException("Token de credencial de Google ausente o invalido.");
        }

        String[] parts = credential.trim().split("\\.");
        if (parts.length != 3 || parts[2].isBlank()) {
            throw new BadCredentialsException("Formato de token ID de Google invalido o sin firma criptografica.");
        }

        try {
            String rawResponse = googleVerifierRestTemplate.getForObject(GOOGLE_TOKENINFO_URL, String.class, credential.trim());
            if (rawResponse == null || rawResponse.isBlank()) {
                throw new BadCredentialsException("Respuesta vacia al verificar el token con Google.");
            }

            JsonNode claims = objectMapper.readTree(rawResponse);
            String issuer = claims.path("iss").asText("");
            if (!"accounts.google.com".equals(issuer) && !"https://accounts.google.com".equals(issuer)) {
                throw new BadCredentialsException("Emisor del token de Google no reconocido.");
            }

            boolean emailVerified = "true".equalsIgnoreCase(claims.path("email_verified").asText())
                    || claims.path("email_verified").asBoolean(false);
            if (!emailVerified) {
                throw new BadCredentialsException("El correo de la cuenta de Google no esta verificado.");
            }

            long expEpochSeconds = claims.path("exp").asLong(0L);
            if (expEpochSeconds > 0 && expEpochSeconds <= Instant.now().getEpochSecond()) {
                throw new BadCredentialsException("El token de autenticacion de Google ha expirado.");
            }

            if (googleClientId != null && !googleClientId.isBlank()) {
                String aud = claims.path("aud").asText("");
                if (!googleClientId.trim().equals(aud)) {
                    throw new BadCredentialsException("El token de Google no corresponde a esta aplicacion (aud invalido).");
                }
            }

            return claims;
        } catch (BadCredentialsException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BadCredentialsException("Token de Google rechazado durante la verificacion criptografica.");
        } catch (Exception e) {
            throw new BadCredentialsException("No fue posible verificar la autenticidad del token de Google.");
        }
    }

    @Transactional
    public AuthResponse loginAsDemo() {
        String email = "demo@deployops.io";
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = new UserEntity();
            user.setName("Usuario Demo (SRE)");
            user.setEmail(email);
            user.setProvider(AuthProvider.LOCAL);
            user.setRole(UserRole.ROLE_USER);
            user.setCreatedAt(LocalDateTime.now());
            String demoSlug = userRepository.existsByPublicSlug("demo-user")
                    ? generateUniqueSlug("demo-user")
                    : "demo-user";
            user.setPublicSlug(demoSlug);
            user = userRepository.save(user);
        }

        seedDemoEndpointsIfEmpty(user);

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user);
    }

    private void seedDemoEndpointsIfEmpty(UserEntity demoUser) {
        if (demoUser == null || demoUser.getId() == null) {
            return;
        }
        List<ServiceEntity> existing = serviceRepository.findByUserOrderByIdDesc(demoUser);
        if (existing != null && !existing.isEmpty()) {
            return;
        }

        Object[][] samples = new Object[][]{
                {"GitHub Rest API", "https://api.github.com", 118L},
                {"Cloudflare Edge DNS", "https://1.1.1.1", 24L},
                {"Wikipedia Global", "https://www.wikipedia.org", 142L}
        };

        LocalDateTime now = LocalDateTime.now();
        for (Object[] sample : samples) {
            ServiceEntity s = new ServiceEntity();
            s.setName((String) sample[0]);
            s.setUrl((String) sample[1]);
            s.setStatus(ServiceStatus.HEALTHY);
            s.setConsecutiveFailures(0);
            s.setConsecutiveSuccesses(2);
            s.setLastResponseTimeMs((Long) sample[2]);
            s.setLastCheckedAt(now);
            s.setCreatedAt(now.minusHours(6));
            s.setProtected(false);
            s.setPublicStatus(true);
            s.setHttpMethod("GET");
            s.setUser(demoUser);
            serviceRepository.save(s);
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

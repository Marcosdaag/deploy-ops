package com.marcosdaag.deployops.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuthService authService;

    private UserEntity sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new UserEntity();
        sampleUser.setId(1L);
        sampleUser.setName("Marcos Dev");
        sampleUser.setEmail("marcos@example.com");
        sampleUser.setPassword("encodedHash123");
        sampleUser.setRole(UserRole.ROLE_USER);
        sampleUser.setProvider(AuthProvider.LOCAL);
    }

    @Test
    @DisplayName("Registra un nuevo usuario correctamente y genera token JWT")
    void whenRegisterNewUser_shouldSucceed() {
        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setName("Marcos Dev");
        request.setEmail("marcos@example.com");
        request.setPassword("Secret123!");

        when(userRepository.existsByEmail("marcos@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedHash123");
        when(userRepository.save(any(UserEntity.class))).thenReturn(sampleUser);
        when(jwtService.generateToken(sampleUser)).thenReturn("mocked.jwt.token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.getToken());
        assertEquals("marcos@example.com", response.getEmail());
        assertEquals("Marcos Dev", response.getName());
        assertEquals("ROLE_USER", response.getRole());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Falla al registrar con un correo ya existente")
    void whenRegisterWithDuplicateEmail_shouldThrowException() {
        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setName("Otro Usuario");
        request.setEmail("marcos@example.com");
        request.setPassword("Secret123!");

        when(userRepository.existsByEmail("marcos@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Inicia sesion correctamente con credenciales validas")
    void whenLoginWithValidCredentials_shouldReturnToken() {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setEmail("marcos@example.com");
        request.setPassword("Secret123!");

        when(userRepository.findByEmail("marcos@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Secret123!", "encodedHash123")).thenReturn(true);
        when(jwtService.generateToken(sampleUser)).thenReturn("mocked.jwt.token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.getToken());
        assertEquals("marcos@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Rechaza login si la contrasena es incorrecta")
    void whenLoginWithInvalidPassword_shouldThrowBadCredentials() {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setEmail("marcos@example.com");
        request.setPassword("WrongPassword");

        when(userRepository.findByEmail("marcos@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", "encodedHash123")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Rechaza login si el correo no existe")
    void whenLoginWithNonExistentEmail_shouldThrowBadCredentials() {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setEmail("inexistente@example.com");
        request.setPassword("Secret123!");

        when(userRepository.findByEmail("inexistente@example.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Impide login clasico en cuentas creadas con Google (proteccion contra toma de cuenta)")
    void whenLoginWithGoogleAccountWithoutPassword_shouldThrowBadCredentials() {
        UserEntity googleUser = new UserEntity();
        googleUser.setId(3L);
        googleUser.setEmail("googleuser@gmail.com");
        googleUser.setPassword(null);
        googleUser.setProvider(AuthProvider.GOOGLE);

        AuthLoginRequest request = new AuthLoginRequest();
        request.setEmail("googleuser@gmail.com");
        request.setPassword("AttackerGuess123");

        when(userRepository.findByEmail("googleuser@gmail.com")).thenReturn(Optional.of(googleUser));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Autentica o registra usuario de Google a partir del token JWT de Google")
    void whenLoginWithGoogle_shouldParsePayloadAndAuthenticate() {
        String payloadJson = "{\"email\":\"googleuser@gmail.com\",\"name\":\"Google User\",\"sub\":\"1234567890\"}";
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String dummyGoogleToken = "header." + encodedPayload + ".signature";

        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setCredential(dummyGoogleToken);

        UserEntity googleUser = new UserEntity();
        googleUser.setId(2L);
        googleUser.setName("Google User");
        googleUser.setEmail("googleuser@gmail.com");
        googleUser.setProvider(AuthProvider.GOOGLE);
        googleUser.setRole(UserRole.ROLE_USER);

        when(userRepository.findByEmail("googleuser@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(googleUser);
        when(jwtService.generateToken(googleUser)).thenReturn("app.jwt.for.google");

        AuthResponse response = authService.loginWithGoogle(request);

        assertNotNull(response);
        assertEquals("app.jwt.for.google", response.getToken());
        assertEquals("googleuser@gmail.com", response.getEmail());
        assertEquals("GOOGLE", response.getProvider());
    }

    @Test
    @DisplayName("Obtiene los datos del usuario autenticado actual")
    void whenGetCurrentUser_shouldReturnUserData() {
        when(userRepository.findByEmail("marcos@example.com")).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken(sampleUser)).thenReturn("current.jwt.token");

        AuthResponse response = authService.getCurrentUserResponse("marcos@example.com");

        assertNotNull(response);
        assertEquals("marcos@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Inicia sesion en modo demo creando el usuario si no existe")
    void whenLoginAsDemo_shouldCreateOrReturnDemoUser() {
        when(userRepository.findByEmail("demo@deployops.io")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity u = invocation.getArgument(0);
            u.setId(999L);
            return u;
        });
        when(jwtService.generateToken(any(UserEntity.class))).thenReturn("demo.jwt.token");

        AuthResponse response = authService.loginAsDemo();

        assertNotNull(response);
        assertEquals("demo.jwt.token", response.getToken());
        assertEquals("demo@deployops.io", response.getEmail());
        assertEquals("Usuario Demo (SRE)", response.getName());
    }

    @Test
    @DisplayName("Lanza ResourceNotFoundException si el usuario actual no existe")
    void whenGetCurrentUserNotFound_shouldThrowException() {
        when(userRepository.findByEmail("nadie@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.getCurrentUserResponse("nadie@example.com"));
    }
}

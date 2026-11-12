package com.marcosdaag.deployops.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("Endpoint /actuator/health es accesible publicamente sin autenticacion")
    void whenAccessingActuatorHealth_shouldReturnOkOrUp() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/actuator/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertNotNull(response.body());
        assertTrue(response.body().contains("UP"));
    }

    @Test
    @DisplayName("Endpoint /actuator/info es accesible publicamente sin autenticacion")
    void whenAccessingActuatorInfo_shouldReturnOk() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/actuator/info"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    @Test
    @DisplayName("Endpoints de actuator restringidos como /actuator/env no son permitidos publicamente")
    void whenAccessingRestrictedActuator_shouldNotBePermitted() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/actuator/env"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        assertTrue(
                status == 401 || status == 403 || status == 404,
                "Esperado 401, 403 o 404 pero se obtuvo: " + status
        );
    }

    @Test
    @DisplayName("Endpoint /api/chaos/state es accesible sin autenticacion para modo demo/showcase")
    void whenAccessingChaosStateWithoutAuth_shouldBePermitted() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/chaos/state"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }

    @Test
    @DisplayName("Endpoints protegidos como /api/webhooks requieren autenticacion devolviendo 401 o 403")
    void whenAccessingProtectedEndpointWithoutAuth_shouldRequireAuthentication() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/webhooks"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        assertTrue(
                status == 401 || status == 403,
                "Esperado 401 o 403 pero se obtuvo: " + status
        );
    }
}

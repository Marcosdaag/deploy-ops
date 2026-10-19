package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.PingResult;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Service
public class HttpMonitorService {

    private final RestTemplate restTemplate;

    public HttpMonitorService() {
        // Configuramos timeouts estrictos para no bloquear los hilos del scheduler
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Ejecuta una petición HTTP capturando latencia en ms, código de respuesta y errores.
     */
    public PingResult ping(String url) {
        long startTime = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            long elapsed = System.currentTimeMillis() - startTime;
            int statusCode = response.getStatusCode().value();
            boolean isAlive = response.getStatusCode().is2xxSuccessful();

            return isAlive
                    ? PingResult.success(statusCode, elapsed)
                    : PingResult.failure(statusCode, elapsed, "Código de respuesta no exitoso: " + statusCode);

        } catch (HttpStatusCodeException e) {
            // El servidor respondió pero con error 4xx o 5xx
            long elapsed = System.currentTimeMillis() - startTime;
            return PingResult.failure(e.getStatusCode().value(), elapsed, "HTTP error: " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            // Timeout de conexión, lectura o DNS no resuelto
            long elapsed = System.currentTimeMillis() - startTime;
            return PingResult.failure(0, elapsed, "Timeout o fallo de conectividad");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            return PingResult.failure(0, elapsed, "Error al chequear servicio: " + e.getMessage());
        }
    }

    /**
     * Helper retrocompatible que retorna únicamente si el servicio está operativo.
     */
    public boolean isUrlAlive(String url) {
        return ping(url).isAlive();
    }
}
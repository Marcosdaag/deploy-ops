package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.PingResult;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class HttpMonitorService {

    private final RestTemplate restTemplate;

    // Configuramos RestTemplate con timeouts para que una URL colgada no trabe el servidor
    public HttpMonitorService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restTemplate = new RestTemplate(factory);
    }

    // Hace la peticion HTTP y mide cuanto tarda en responder
    public PingResult ping(String url) {
        long startNano = System.nanoTime();
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            int statusCode = response.getStatusCode().value();
            boolean isAlive = response.getStatusCode().is2xxSuccessful();

            return isAlive
                    ? PingResult.success(statusCode, elapsedMs)
                    : PingResult.failure(statusCode, elapsedMs, "Codigo de respuesta: " + statusCode);

        } catch (HttpStatusCodeException e) {
            // Respondio pero con error 4xx o 5xx
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            return PingResult.failure(e.getStatusCode().value(), elapsedMs, "HTTP error: " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            // Timeout o la URL no existe
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            return PingResult.failure(0, elapsedMs, "Timeout o fallo de conexion");
        } catch (Exception e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            return PingResult.failure(0, elapsedMs, "Error al chequear servicio");
        }
    }

    public boolean isUrlAlive(String url) {
        return ping(url).isAlive();
    }
}

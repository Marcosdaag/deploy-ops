package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.dto.PingResult;
import com.marcosdaag.deployops.model.ServiceEntity;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

    public HttpMonitorService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restTemplate = new RestTemplate(factory);
    }

    public PingResult ping(String url) {
        ServiceEntity dummy = new ServiceEntity();
        dummy.setUrl(url);
        dummy.setHttpMethod("GET");
        return ping(dummy);
    }

    public PingResult ping(ServiceEntity service) {
        long startNano = System.nanoTime();
        try {
            HttpMethod method = resolveHttpMethod(service.getHttpMethod());
            HttpHeaders headers = parseHeaders(service.getCustomHeaders());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Void> response = restTemplate.exchange(service.getUrl(), method, entity, Void.class);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            int statusCode = response.getStatusCode().value();

            boolean isAlive = evaluateLiveness(statusCode, service.getExpectedStatusCode());
            return isAlive
                    ? PingResult.success(statusCode, elapsedMs)
                    : PingResult.failure(statusCode, elapsedMs, "Codigo de respuesta inesperado: " + statusCode);

        } catch (HttpStatusCodeException e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
            int statusCode = e.getStatusCode().value();
            boolean isAlive = evaluateLiveness(statusCode, service.getExpectedStatusCode());
            return isAlive
                    ? PingResult.success(statusCode, elapsedMs)
                    : PingResult.failure(statusCode, elapsedMs, "HTTP error: " + statusCode);
        } catch (ResourceAccessException e) {
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

    private HttpMethod resolveHttpMethod(String rawMethod) {
        if (rawMethod == null || rawMethod.isBlank()) {
            return HttpMethod.GET;
        }
        try {
            return HttpMethod.valueOf(rawMethod.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return HttpMethod.GET;
        }
    }

    private HttpHeaders parseHeaders(String customHeaders) {
        HttpHeaders headers = new HttpHeaders();
        if (customHeaders == null || customHeaders.isBlank()) {
            return headers;
        }

        String[] lines = customHeaders.split("[\\r\\n]+");
        for (String line : lines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0 && colonIndex < line.length() - 1) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    headers.add(key, value);
                }
            }
        }
        return headers;
    }

    private boolean evaluateLiveness(int actualCode, Integer expectedCode) {
        if (expectedCode != null && expectedCode > 0) {
            return actualCode == expectedCode;
        }
        return actualCode >= 200 && actualCode < 400;
    }
}

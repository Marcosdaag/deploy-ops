package com.marcosdaag.deployops.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

@Service
public class HttpMonitorService {
    private final RestTemplate restTemplate;

    // Inyectamos RestTemplate que es la herramienta de spring para hacer peticiones web
    public HttpMonitorService() {
        this.restTemplate = new RestTemplate();
    }

    // Este metodo hace una peticion y devuelve TRUE o FALSE
    public boolean isUrlAlive(String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            // Si el código de estado es 200, 201, etc. (Empieza con 2xx), está viva
            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            // Si la URL no existe, da timeout, o da error 500, capturamos el error y devolvemos FALSE
            return false;
        }
    }
}
package com.marcosdaag.deployops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Limpiamos espacios y barras finales por si la URL en variables de entorno trae un "/" al final
        String[] cleanOrigins = Arrays.stream(allowedOrigins)
                .map(origin -> origin.trim().replaceAll("/+$", ""))
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        registry.addMapping("/api/**")
                .allowedOriginPatterns(cleanOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}

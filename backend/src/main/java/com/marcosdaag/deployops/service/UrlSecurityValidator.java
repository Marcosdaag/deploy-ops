package com.marcosdaag.deployops.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

// Valida que no nos pasen URLs locales (localhost o IPs privadas) para evitar ataques SSRF
@Component
public class UrlSecurityValidator {

    public void validatePublicUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("La URL no puede estar vacia.");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El formato de la URL no es valido.");
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Solo se permiten protocolos HTTP o HTTPS.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("La URL debe contener un host valido.");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);

        // Bloqueamos localhost y direcciones internas
        if (normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".local")
                || normalizedHost.endsWith(".internal")
                || normalizedHost.equals("169.254.169.254")) {
            throw new IllegalArgumentException("No se permite monitorear hosts locales ni internos.");
        }

        // Revisamos que la IP no sea privada (ej. 127.0.0.1 o 192.168.x.x)
        try {
            InetAddress address = InetAddress.getByName(normalizedHost);
            if (address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress()) {
                throw new IllegalArgumentException("No se permite monitorear direcciones IP privadas o locales.");
            }
        } catch (UnknownHostException ignored) {
            // Si todavia no resuelve el dominio, lo dejamos pasar y el ping lo marcara si falla
        }
    }
}

package com.marcosdaag.deployops.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlSecurityValidator {

    private static final Set<Integer> BLOCKED_PORTS = Set.of(
            21, 22, 23, 25, 53, 69, 88, 110, 119, 123, 135, 137, 138, 139, 143,
            161, 162, 389, 445, 465, 587, 1433, 1521, 2049, 2375, 2376, 2379, 2380,
            3306, 3389, 5432, 5900, 6379, 9200, 9300, 10250, 10255, 11211, 27017, 27018
    );

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

        int port = uri.getPort();
        if (port > 0) {
            if (port > 65535) {
                throw new IllegalArgumentException("El puerto especificado no es valido.");
            }
            if (BLOCKED_PORTS.contains(port)) {
                throw new IllegalArgumentException("El puerto especificado (" + port + ") no esta permitido por seguridad.");
            }
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);

        if (normalizedHost.equals("localhost")
                || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")
                || normalizedHost.endsWith(".internal")
                || normalizedHost.endsWith(".arpa")
                || normalizedHost.endsWith(".invalid")
                || normalizedHost.equals("169.254.169.254")
                || normalizedHost.equals("fd00:ec2::254")) {
            throw new IllegalArgumentException("No se permite monitorear hosts locales ni internos.");
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(normalizedHost);
            for (InetAddress address : addresses) {
                if (isRestrictedIp(address)) {
                    throw new IllegalArgumentException("No se permite monitorear direcciones IP privadas o locales.");
                }
            }
        } catch (UnknownHostException ignored) {
            // Si el dominio no resuelve todavia, el ping fallara naturalmente al conectarse
        }
    }

    private boolean isRestrictedIp(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // 0.0.0.0/8
            if (b0 == 0) return true;
            // 100.64.0.0/10 (Carrier-Grade NAT RFC 6598)
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) return true;
            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true;
            // 169.254.0.0/16 (Link Local / Cloud Metadata)
            if (b0 == 169 && b1 == 254) return true;
            // 192.0.0.0/24 (IETF Protocol Assignments)
            if (b0 == 192 && b1 == 0 && (bytes[2] & 0xFF) == 0) return true;
            // 198.18.0.0/15 (Benchmarking)
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return true;
            // 240.0.0.0/4 (Reserved / Future)
            if (b0 >= 240) return true;
        } else if (bytes.length == 16) {
            // IPv6 Unique Local Address fc00::/7 (incluye metadata de AWS fd00:ec2::254)
            int b0 = bytes[0] & 0xFF;
            if ((b0 & 0xFE) == 0xFC) {
                return true;
            }

            // IPv4-mapped IPv6 ::ffff:x.x.x.x
            boolean isIpv4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    isIpv4Mapped = false;
                    break;
                }
            }
            if (isIpv4Mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                int m0 = bytes[12] & 0xFF;
                int m1 = bytes[13] & 0xFF;
                if (m0 == 0 || m0 == 127 || m0 == 10
                        || (m0 == 172 && m1 >= 16 && m1 <= 31)
                        || (m0 == 192 && m1 == 168)
                        || (m0 == 169 && m1 == 254)
                        || (m0 == 100 && m1 >= 64 && m1 <= 127)
                        || m0 >= 240) {
                    return true;
                }
            }
        }

        return false;
    }
}

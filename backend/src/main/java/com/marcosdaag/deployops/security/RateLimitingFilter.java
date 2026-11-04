package com.marcosdaag.deployops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcosdaag.deployops.dto.ErrorResponseDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = extractClientIp(request);

        boolean allowed = true;

        if (path.startsWith("/api/auth/login") && "POST".equalsIgnoreCase(method)) {
            allowed = rateLimiterService.tryAcquire("login:" + clientIp, 10, 60_000);
        } else if (path.startsWith("/api/auth/register") && "POST".equalsIgnoreCase(method)) {
            allowed = rateLimiterService.tryAcquire("register:" + clientIp, 5, 60_000);
        } else if (path.matches("^/api/endpoints/\\d+/ping$") && "POST".equalsIgnoreCase(method)) {
            allowed = rateLimiterService.tryAcquire("ping:" + clientIp, 20, 60_000);
        } else if (path.matches("^/api/endpoints/\\d+/report/csv$") && "GET".equalsIgnoreCase(method)) {
            allowed = rateLimiterService.tryAcquire("report_csv:" + clientIp, 10, 60_000);
        }

        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", "60");

            ErrorResponseDTO error = ErrorResponseDTO.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Demasiadas solicitudes",
                    "Has superado el limite de peticiones permitidas para este recurso. Por favor, espera un minuto."
            );
            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}

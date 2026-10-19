package com.marcosdaag.deployops.dto;

/**
 * Representa el resultado detallado de un chequeo HTTP.
 */
public record PingResult(
        boolean isAlive,
        int statusCode,
        long responseTimeMs,
        String errorMessage
) {
    public static PingResult success(int statusCode, long responseTimeMs) {
        return new PingResult(true, statusCode, responseTimeMs, null);
    }

    public static PingResult failure(int statusCode, long responseTimeMs, String errorMessage) {
        return new PingResult(false, statusCode, responseTimeMs, errorMessage);
    }
}

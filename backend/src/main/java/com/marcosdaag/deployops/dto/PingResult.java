package com.marcosdaag.deployops.dto;

// Guarda el resultado de cada ping (si respondio, codigo HTTP y cuanto tardo en ms)
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

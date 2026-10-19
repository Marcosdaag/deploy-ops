package com.marcosdaag.deployops.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponseDTO(
        int status,
        String error,
        List<String> messages,
        LocalDateTime timestamp
) {
    public static ErrorResponseDTO of(int status, String error, List<String> messages) {
        return new ErrorResponseDTO(status, error, messages, LocalDateTime.now());
    }

    public static ErrorResponseDTO of(int status, String error, String message) {
        return new ErrorResponseDTO(status, error, List.of(message), LocalDateTime.now());
    }
}

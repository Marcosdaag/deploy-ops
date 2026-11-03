package com.marcosdaag.deployops.dto;

import java.time.LocalDateTime;

public record SslCertificateInfo(
        Integer daysRemaining,
        LocalDateTime expiryDate,
        String issuer
) {}

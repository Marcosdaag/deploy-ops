package com.marcosdaag.deployops.model;

import com.marcosdaag.deployops.model.enums.WebhookProviderType;
import com.marcosdaag.deployops.security.SecretCryptoConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "webhook_endpoints", indexes = {
        @Index(name = "idx_webhook_user_id", columnList = "user_id"),
        @Index(name = "idx_webhook_enabled", columnList = "enabled")
})
public class WebhookEndpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookProviderType providerType = WebhookProviderType.CUSTOM;

    @Convert(converter = SecretCryptoConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String signingSecret;

    @Column(length = 30)
    private String secretHint;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 100)
    private String lastDeliveryStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}

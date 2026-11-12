package com.marcosdaag.deployops.model;

import com.marcosdaag.deployops.model.enums.ServiceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "services", indexes = {
        @Index(name = "idx_services_user_id", columnList = "user_id"),
        @Index(name = "idx_services_url", columnList = "url")
})
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status = ServiceStatus.PENDING;

    @Column(nullable = false)
    private int consecutiveFailures = 0;

    @Column(nullable = false)
    private int consecutiveSuccesses = 0;

    private Long lastResponseTimeMs;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastCheckedAt;

    @Column(name = "is_protected")
    private boolean isProtected = false;

    @Column(name = "is_public_status")
    private boolean isPublicStatus = false;

    @Column(length = 10)
    private String httpMethod = "GET";

    @Column
    private Integer expectedStatusCode;

    @Column(length = 1000)
    private String customHeaders;

    @Column
    private Integer sslDaysRemaining;

    @Column
    private LocalDateTime sslExpiryDate;

    @Column(length = 150)
    private String sslIssuer;

    @Column
    private LocalDateTime lastSslCheckedAt;

    @Column
    private LocalDateTime lastSslAlertSentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    public boolean isProtected() {
        return isProtected;
    }

    public void setProtected(boolean isProtected) {
        this.isProtected = isProtected;
    }

    public LocalDateTime getLastSslCheckedAt() {
        return lastSslCheckedAt;
    }

    public void setLastSslCheckedAt(LocalDateTime lastSslCheckedAt) {
        this.lastSslCheckedAt = lastSslCheckedAt;
    }

    public LocalDateTime getLastSslAlertSentAt() {
        return lastSslAlertSentAt;
    }

    public void setLastSslAlertSentAt(LocalDateTime lastSslAlertSentAt) {
        this.lastSslAlertSentAt = lastSslAlertSentAt;
    }
}

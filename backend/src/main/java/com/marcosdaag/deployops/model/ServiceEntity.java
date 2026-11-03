package com.marcosdaag.deployops.model;

import com.marcosdaag.deployops.model.enums.ServiceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "services")
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

    @Column(name = "is_protected", nullable = false)
    private boolean isProtected = false;

    @Column(name = "is_public_status", nullable = false)
    private boolean isPublicStatus = false;

    @Column(length = 10, nullable = false)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    public boolean isProtected() {
        return isProtected;
    }

    public void setProtected(boolean isProtected) {
        this.isProtected = isProtected;
    }
}

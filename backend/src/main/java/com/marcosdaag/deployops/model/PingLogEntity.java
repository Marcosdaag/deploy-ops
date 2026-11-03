package com.marcosdaag.deployops.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "ping_logs", indexes = {
        @Index(name = "idx_ping_service_timestamp", columnList = "service_id, timestamp")
})
public class PingLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceEntity service;

    @Column(nullable = false)
    private Long responseTimeMs;

    @Column(nullable = false)
    private Integer statusCode;

    @Column(nullable = false)
    private boolean isAlive;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
}

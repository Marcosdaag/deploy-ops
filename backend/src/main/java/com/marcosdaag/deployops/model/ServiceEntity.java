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

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceStatus status = ServiceStatus.HEALTHY;

    // Contadores para la máquina de estados de monitoreo
    @Column(nullable = false)
    private int consecutiveFailures = 0;

    @Column(nullable = false)
    private int consecutiveSuccesses = 0;

    // Fechas de auditoría y cálculo de métricas
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime lastCheckedAt;
}
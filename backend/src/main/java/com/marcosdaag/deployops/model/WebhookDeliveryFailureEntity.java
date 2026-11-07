package com.marcosdaag.deployops.model;

import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.model.enums.WebhookDeliveryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "webhook_delivery_failures",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_failure_event_webhook", columnNames = {"event_id", "webhook_id"})
        },
        indexes = {
                @Index(name = "idx_failure_user_status", columnList = "user_id, status"),
                @Index(name = "idx_failure_failed_at", columnList = "failed_at")
        }
)
public class WebhookDeliveryFailureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_id")
    private WebhookEndpointEntity webhook;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OutboxEventType eventType;

    @Column
    private Integer httpStatus;

    @Column(length = 1000)
    private String errorDetail;

    @Column(nullable = false)
    private int attemptCount = 0;

    @Column(columnDefinition = "TEXT")
    private String payloadSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookDeliveryStatus status = WebhookDeliveryStatus.DEAD_LETTER;

    @Column(nullable = false)
    private LocalDateTime failedAt = LocalDateTime.now();

    @Column
    private LocalDateTime resolvedAt;
}

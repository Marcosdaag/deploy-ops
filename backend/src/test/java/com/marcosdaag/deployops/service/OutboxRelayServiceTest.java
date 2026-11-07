package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.WebhookEndpointEntity;
import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.model.enums.OutboxStatus;
import com.marcosdaag.deployops.repository.OutboxEventRepository;
import com.marcosdaag.deployops.repository.UserRepository;
import com.marcosdaag.deployops.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WebhookDeliveryService webhookDeliveryService;

    private OutboxRelayService outboxRelayService;

    @BeforeEach
    void setUp() {
        outboxRelayService = new OutboxRelayService(
                outboxEventRepository,
                webhookEndpointRepository,
                userRepository,
                webhookDeliveryService,
                (org.springframework.amqp.rabbit.core.RabbitTemplate) null, // no rabbitTemplate in standalone unit test
                false // rabbitEnabled = false (virtual threads fallback)
        );
    }

    @Test
    @DisplayName("Rescata eventos huérfanos estancados en PROCESSING (> 2 min) y los regresa a PENDING")
    void shouldReapZombieLocks() {
        OutboxEventEntity zombie = new OutboxEventEntity();
        zombie.setId("zombie-1");
        zombie.setStatus(OutboxStatus.PROCESSING);
        zombie.setLockedAt(LocalDateTime.now().minusMinutes(3));

        when(outboxEventRepository.findStaleProcessingEvents(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(zombie));

        outboxRelayService.reapZombieLocks();

        assertEquals(OutboxStatus.PENDING, zombie.getStatus());
        assertNull(zombie.getLockedAt());
        verify(outboxEventRepository, times(1)).saveAll(List.of(zombie));
    }

    @Test
    @DisplayName("Procesa eventos PENDING y los despacha mediante respaldo de Virtual Threads")
    void shouldRelayPendingEventsViaVirtualThreads() {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId("evt-1");
        event.setStatus(OutboxStatus.PENDING);
        event.setEventType(OutboxEventType.INCIDENT_OPENED);
        event.setPayloadJson("{\"eventId\":\"evt-1\"}");
        event.setUserId(1L);

        UserEntity user = new UserEntity();
        user.setId(1L);

        WebhookEndpointEntity webhook = new WebhookEndpointEntity();
        webhook.setId(10L);
        webhook.setEnabled(true);

        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(outboxEventRepository.saveAll(any())).thenReturn(List.of(event));
        when(outboxEventRepository.findById("evt-1")).thenReturn(Optional.of(event));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(webhookEndpointRepository.findByUserAndEnabledTrue(user)).thenReturn(List.of(webhook));

        outboxRelayService.relayPendingEvents();

        assertEquals(OutboxStatus.PROCESSED, event.getStatus());
        assertNotNull(event.getProcessedAt());
        verify(outboxEventRepository, atLeastOnce()).save(event);
    }

    @Test
    @DisplayName("Purga eventos procesados de mas de 7 dias")
    void shouldPurgeOldProcessedEvents() {
        when(outboxEventRepository.deleteOldProcessedEvents(eq(OutboxStatus.PROCESSED), any(LocalDateTime.class)))
                .thenReturn(15);

        outboxRelayService.purgeOldProcessedEvents();

        verify(outboxEventRepository, times(1))
                .deleteOldProcessedEvents(eq(OutboxStatus.PROCESSED), any(LocalDateTime.class));
    }
}

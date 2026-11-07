package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.WebhookEndpointEntity;
import com.marcosdaag.deployops.model.enums.OutboxStatus;
import com.marcosdaag.deployops.repository.OutboxEventRepository;
import com.marcosdaag.deployops.repository.UserRepository;
import com.marcosdaag.deployops.repository.WebhookEndpointRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class OutboxRelayService {

    private static final Logger logger = LoggerFactory.getLogger(OutboxRelayService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final UserRepository userRepository;
    private final WebhookDeliveryService webhookDeliveryService;
    private final RabbitTemplate rabbitTemplate;
    private final boolean rabbitEnabled;
    private final ExecutorService relayExecutor;

    @Autowired
    public OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            WebhookEndpointRepository webhookEndpointRepository,
            UserRepository userRepository,
            WebhookDeliveryService webhookDeliveryService,
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
            @Value("${app.rabbitmq.enabled:false}") boolean rabbitEnabled
    ) {
        this(
                outboxEventRepository,
                webhookEndpointRepository,
                userRepository,
                webhookDeliveryService,
                rabbitTemplateProvider != null ? rabbitTemplateProvider.getIfAvailable() : null,
                rabbitEnabled
        );
    }

    public OutboxRelayService(
            OutboxEventRepository outboxEventRepository,
            WebhookEndpointRepository webhookEndpointRepository,
            UserRepository userRepository,
            WebhookDeliveryService webhookDeliveryService,
            RabbitTemplate rabbitTemplate,
            boolean rabbitEnabled
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.userRepository = userRepository;
        this.webhookDeliveryService = webhookDeliveryService;
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitEnabled = rabbitEnabled && this.rabbitTemplate != null;
        this.relayExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "outboxRelaySweepLock", lockAtMostFor = "4s", lockAtLeastFor = "1s")
    public void relayPendingEvents() {
        // 1. Rescatar eventos huérfanos en PROCESSING estancados por caídas de nodos (> 2 min)
        reapZombieLocks();

        // 2. Procesar lote de eventos PENDING
        List<OutboxEventEntity> pendingEvents = fetchAndLockPendingBatch();
        if (pendingEvents.isEmpty()) {
            return;
        }

        logger.debug("Despachando lote de {} eventos Outbox...", pendingEvents.size());

        for (OutboxEventEntity event : pendingEvents) {
            try {
                if (rabbitEnabled && rabbitTemplate != null) {
                    dispatchViaRabbit(event);
                } else {
                    dispatchViaVirtualThreads(event);
                }
                markEventProcessed(event.getId());
            } catch (Exception ex) {
                logger.error("Error al despachar evento Outbox {}: {}", event.getId(), ex.getMessage());
                markEventFailed(event.getId(), ex.getMessage());
            }
        }
    }

    @Transactional
    public List<OutboxEventEntity> fetchAndLockPendingBatch() {
        List<OutboxEventEntity> batch = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, 50)
        );

        LocalDateTime now = LocalDateTime.now();
        for (OutboxEventEntity event : batch) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLockedAt(now);
            event.setAttemptCount(event.getAttemptCount() + 1);
        }
        return outboxEventRepository.saveAll(batch);
    }

    @Transactional
    public void markEventProcessed(String eventId) {
        outboxEventRepository.findById(eventId).ifPresent(e -> {
            e.setStatus(OutboxStatus.PROCESSED);
            e.setProcessedAt(LocalDateTime.now());
            e.setLockedAt(null);
            outboxEventRepository.save(e);
        });
    }

    @Transactional
    public void markEventFailed(String eventId, String reason) {
        outboxEventRepository.findById(eventId).ifPresent(e -> {
            if (e.getAttemptCount() >= 3) {
                e.setStatus(OutboxStatus.FAILED);
            } else {
                e.setStatus(OutboxStatus.PENDING);
            }
            e.setLockedAt(null);
            outboxEventRepository.save(e);
        });
    }

    @Transactional
    public void reapZombieLocks() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);
        List<OutboxEventEntity> zombies = outboxEventRepository.findStaleProcessingEvents(threshold, PageRequest.of(0, 50));
        if (!zombies.isEmpty()) {
            logger.warn("Rescatando {} eventos Outbox en estado PROCESSING abandonados.", zombies.size());
            for (OutboxEventEntity zombie : zombies) {
                zombie.setStatus(OutboxStatus.PENDING);
                zombie.setLockedAt(null);
            }
            outboxEventRepository.saveAll(zombies);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "outboxPurgeLock", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional
    public void purgeOldProcessedEvents() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        int deleted = outboxEventRepository.deleteOldProcessedEvents(OutboxStatus.PROCESSED, sevenDaysAgo);
        if (deleted > 0) {
            logger.info("Purga de Outbox completada: {} eventos PROCESSED eliminados.", deleted);
        }
    }

    private void dispatchViaRabbit(OutboxEventEntity event) {
        String routingKey = "incident." + event.getEventType().name().toLowerCase();
        rabbitTemplate.convertAndSend("deployops.incidents", routingKey, event.getPayloadJson());
        logger.info("Evento Outbox {} publicado en RabbitMQ [Exchange: deployops.incidents, Key: {}]",
                event.getId(), routingKey);
    }

    private void dispatchViaVirtualThreads(OutboxEventEntity event) {
        List<WebhookEndpointEntity> targets;
        if (event.getUserId() != null) {
            UserEntity owner = userRepository.findById(event.getUserId()).orElse(null);
            targets = (owner != null)
                    ? webhookEndpointRepository.findByUserAndEnabledTrue(owner)
                    : List.of();
        } else {
            targets = webhookEndpointRepository.findByEnabledTrue();
        }

        if (targets.isEmpty()) {
            logger.debug("No hay webhooks activos registrados para despachar el evento {}.", event.getId());
            return;
        }

        for (WebhookEndpointEntity target : targets) {
            relayExecutor.submit(() -> {
                try {
                    webhookDeliveryService.deliver(event, target);
                } catch (Exception e) {
                    logger.error("Fallo al entregar webhook en segundo plano: {}", e.getMessage());
                }
            });
        }
    }
}

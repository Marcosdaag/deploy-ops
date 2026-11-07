package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.config.RabbitMqTopologyConfig;
import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.UserEntity;
import com.marcosdaag.deployops.model.WebhookEndpointEntity;
import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.repository.UserRepository;
import com.marcosdaag.deployops.repository.WebhookEndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Service
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class WebhookDispatchConsumer {

    private static final Logger logger = LoggerFactory.getLogger(WebhookDispatchConsumer.class);

    private final WebhookDeliveryService webhookDeliveryService;
    private final WebhookEndpointRepository webhookEndpointRepository;
    private final UserRepository userRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public WebhookDispatchConsumer(
            WebhookDeliveryService webhookDeliveryService,
            WebhookEndpointRepository webhookEndpointRepository,
            UserRepository userRepository
    ) {
        this.webhookDeliveryService = webhookDeliveryService;
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.userRepository = userRepository;
    }

    @RabbitListener(queues = RabbitMqTopologyConfig.QUEUE_WEBHOOKS_DISPATCH)
    public void consumeWebhookDispatch(String payloadJson) {
        try {
            JsonNode root = jsonMapper.readTree(payloadJson);
            String eventId = root.path("eventId").asText();
            String eventTypeStr = root.path("eventType").asText();
            Long userId = root.has("userId") && !root.path("userId").isNull() ? root.path("userId").asLong() : null;

            OutboxEventType eventType = OutboxEventType.valueOf(eventTypeStr);

            OutboxEventEntity dummyEvent = new OutboxEventEntity();
            dummyEvent.setId(eventId);
            dummyEvent.setEventType(eventType);
            dummyEvent.setPayloadJson(payloadJson);
            dummyEvent.setUserId(userId);

            List<WebhookEndpointEntity> targets;
            if (userId != null) {
                UserEntity user = userRepository.findById(userId).orElse(null);
                targets = (user != null) ? webhookEndpointRepository.findByUserAndEnabledTrue(user) : List.of();
            } else {
                targets = webhookEndpointRepository.findByEnabledTrue();
            }

            logger.info("Consumidor AMQP: procesando evento {} para {} webhooks destino.", eventId, targets.size());
            for (WebhookEndpointEntity target : targets) {
                webhookDeliveryService.deliver(dummyEvent, target);
            }
        } catch (Exception ex) {
            logger.error("Error al procesar mensaje en cola webhooks.dispatch: {}", ex.getMessage());
            throw new RuntimeException("Reintento en cola AMQP", ex);
        }
    }
}

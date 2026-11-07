package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.config.RabbitMqTopologyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class WebhookDlqConsumer {

    private static final Logger logger = LoggerFactory.getLogger(WebhookDlqConsumer.class);

    @RabbitListener(queues = RabbitMqTopologyConfig.QUEUE_WEBHOOKS_DLQ)
    public void consumeDlqMessage(String payloadJson) {
        logger.warn("Dead Letter Queue (webhooks.dlq) recibio mensaje tras agotar reintentos: {}",
                payloadJson != null && payloadJson.length() > 200 ? payloadJson.substring(0, 200) + "..." : payloadJson);
    }
}

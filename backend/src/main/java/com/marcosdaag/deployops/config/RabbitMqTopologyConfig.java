package com.marcosdaag.deployops.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class RabbitMqTopologyConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMqTopologyConfig.class);

    public static final String EXCHANGE_INCIDENTS = "deployops.incidents";
    public static final String QUEUE_WEBHOOKS_DISPATCH = "webhooks.dispatch";
    public static final String QUEUE_AUDIT_TELEMETRY = "audit.telemetry";

    public static final String DLX_EXCHANGE = "deployops.incidents.dlx";
    public static final String QUEUE_WEBHOOKS_DLQ = "webhooks.dlq";
    public static final String DLX_ROUTING_KEY = "dead.letter";

    public static final String ROUTING_KEY_INCIDENT_WILD = "incident.#";

    @Bean
    public TopicExchange incidentsExchange() {
        logger.info("Declarando TopicExchange AMQP principal: {}", EXCHANGE_INCIDENTS);
        return new TopicExchange(EXCHANGE_INCIDENTS, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        logger.info("Declarando Dead Letter Exchange (DLX) AMQP: {}", DLX_EXCHANGE);
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue webhooksDispatchQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", DLX_ROUTING_KEY);
        return new Queue(QUEUE_WEBHOOKS_DISPATCH, true, false, false, args);
    }

    @Bean
    public Queue auditTelemetryQueue() {
        return new Queue(QUEUE_AUDIT_TELEMETRY, true, false, false);
    }

    @Bean
    public Queue webhooksDlqQueue() {
        return new Queue(QUEUE_WEBHOOKS_DLQ, true, false, false);
    }

    @Bean
    public Binding webhooksDispatchBinding(Queue webhooksDispatchQueue, TopicExchange incidentsExchange) {
        return BindingBuilder.bind(webhooksDispatchQueue).to(incidentsExchange).with(ROUTING_KEY_INCIDENT_WILD);
    }

    @Bean
    public Binding auditTelemetryBinding(Queue auditTelemetryQueue, TopicExchange incidentsExchange) {
        return BindingBuilder.bind(auditTelemetryQueue).to(incidentsExchange).with(ROUTING_KEY_INCIDENT_WILD);
    }

    @Bean
    public Binding webhooksDlqBinding(Queue webhooksDlqQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(webhooksDlqQueue).to(deadLetterExchange).with(DLX_ROUTING_KEY);
    }
}

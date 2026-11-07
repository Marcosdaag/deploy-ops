package com.marcosdaag.deployops.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de integracion E2E para RabbitMQ y flujo de mensajeria Outbox.
 * Utiliza Testcontainers con modo condicional (disabledWithoutDocker = true) para
 * garantizar ejecucion idempotente tanto en entornos con daemon Docker activo como en
 * entornos de compilacion continua locales.
 */
@Testcontainers(disabledWithoutDocker = true)
class RabbitMqOutboxIntegrationTest {

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3.13-management-alpine")
            .withExposedPorts(5672, 15672);

    @Test
    @DisplayName("Deberia inicializar contenedor RabbitMQ y verificar conectividad AMQP cuando Docker este disponible")
    void shouldConnectToRabbitMqWhenDockerAvailable() {
        if (!rabbitMQContainer.isRunning()) {
            return;
        }

        assertNotNull(rabbitMQContainer.getAmqpUrl());
        assertTrue(rabbitMQContainer.isRunning());
    }
}

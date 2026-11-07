package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.model.enums.WebhookProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebhookPayloadFormatterTest {

    private WebhookPayloadFormatter formatter;

    private final String canonicalPayload = """
            {
              "eventId": "evt_123456",
              "serviceName": "API Pagos",
              "url": "https://api.pagos.com/health",
              "status": "DOWN",
              "httpStatus": 503
            }
            """;

    @BeforeEach
    void setUp() {
        formatter = new WebhookPayloadFormatter();
    }

    @Test
    @DisplayName("CUSTOM retorna el payload JSON canonico sin alteraciones")
    void shouldReturnCanonicalPayloadForCustom() {
        String result = formatter.format(WebhookProviderType.CUSTOM, OutboxEventType.INCIDENT_OPENED, canonicalPayload);
        assertEquals(canonicalPayload, result);
    }

    @Test
    @DisplayName("DISCORD formatea como JSON con embeds y color rojo en caida")
    void shouldFormatDiscordPayloadWithRedEmbedOnDown() {
        String result = formatter.format(WebhookProviderType.DISCORD, OutboxEventType.INCIDENT_OPENED, canonicalPayload);

        assertNotNull(result);
        assertTrue(result.contains("\"embeds\""));
        assertTrue(result.contains("API Pagos"));
        assertTrue(result.contains("15158332") || result.contains("15158332") || result.contains(String.valueOf(0xE74C3C)));
    }

    @Test
    @DisplayName("SLACK formatea como JSON con bloques Block Kit")
    void shouldFormatSlackPayloadWithBlocks() {
        String result = formatter.format(WebhookProviderType.SLACK, OutboxEventType.INCIDENT_OPENED, canonicalPayload);

        assertNotNull(result);
        assertTrue(result.contains("\"blocks\""));
        assertTrue(result.contains("API Pagos"));
        assertTrue(result.contains("https://api.pagos.com/health"));
    }
}

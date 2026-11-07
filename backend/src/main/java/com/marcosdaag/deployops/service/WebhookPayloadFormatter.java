package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.model.enums.WebhookProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebhookPayloadFormatter {

    private static final Logger logger = LoggerFactory.getLogger(WebhookPayloadFormatter.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public String format(WebhookProviderType providerType, OutboxEventType eventType, String canonicalPayloadJson) {
        if (providerType == null || providerType == WebhookProviderType.CUSTOM) {
            return canonicalPayloadJson;
        }

        try {
            JsonNode root = jsonMapper.readTree(canonicalPayloadJson);

            String serviceName = root.path("serviceName").asText(root.path("service").path("name").asText("Servicio"));
            String url = root.path("url").asText(root.path("service").path("url").asText("N/A"));
            String status = root.path("status").asText(root.path("service").path("status").asText(eventType.name()));
            int statusCode = root.path("httpStatus").asInt(root.path("service").path("httpStatusCode").asInt(0));
            String eventId = root.path("eventId").asText("N/A");

            boolean isDown = eventType == OutboxEventType.INCIDENT_OPENED;

            if (providerType == WebhookProviderType.DISCORD) {
                return buildDiscordPayload(isDown, serviceName, url, status, statusCode, eventId);
            } else if (providerType == WebhookProviderType.SLACK) {
                return buildSlackPayload(isDown, serviceName, url, status, statusCode, eventId);
            }
        } catch (Exception ex) {
            logger.warn("No se pudo parsear el payload canonico para formateo especial: {}. Enviando crudo.", ex.getMessage());
        }

        return canonicalPayloadJson;
    }

    private String buildDiscordPayload(boolean isDown, String serviceName, String url, String status, int statusCode, String eventId) {
        int color = isDown ? 0xE74C3C : 0x2ECC71; // Rojo caida, Verde recuperacion
        String title = isDown
                ? "[ALERTA] Incidente Detectado: " + serviceName
                : "[RESUELTO] Servicio Recuperado: " + serviceName;

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", title);
        embed.put("color", color);
        embed.put("description", "Notificacion automatica despachada por DeployOps SRE Engine.");

        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(Map.of("name", "Servicio", "value", serviceName, "inline", true));
        fields.add(Map.of("name", "Estado", "value", status, "inline", true));
        fields.add(Map.of("name", "Codigo HTTP", "value", String.valueOf(statusCode), "inline", true));
        fields.add(Map.of("name", "URL Monitoreada", "value", url, "inline", false));
        fields.add(Map.of("name", "ID de Evento", "value", eventId, "inline", false));

        embed.put("fields", fields);

        Map<String, Object> root = new HashMap<>();
        root.put("username", "DeployOps Bot");
        root.put("embeds", List.of(embed));

        try {
            return jsonMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"content\":\"" + title + " - " + url + "\"}";
        }
    }

    private String buildSlackPayload(boolean isDown, String serviceName, String url, String status, int statusCode, String eventId) {
        String alertHeader = isDown
                ? "*[ALERTA DE INCIDENTE]* " + serviceName
                : "*[INCIDENTE RESUELTO]* " + serviceName;

        List<Map<String, Object>> blocks = new ArrayList<>();

        Map<String, Object> headerBlock = new HashMap<>();
        headerBlock.put("type", "header");
        headerBlock.put("text", Map.of("type", "plain_text", "text", alertHeader, "emoji", true));
        blocks.add(headerBlock);

        Map<String, Object> sectionBlock = new HashMap<>();
        sectionBlock.put("type", "section");
        String details = "*URL:* " + url + "\n*Estado:* `" + status + "` (HTTP " + statusCode + ")\n*ID Evento:* `" + eventId + "`";
        sectionBlock.put("text", Map.of("type", "mrkdwn", "text", details));
        blocks.add(sectionBlock);

        Map<String, Object> root = new HashMap<>();
        root.put("text", alertHeader + " - " + url);
        root.put("blocks", blocks);

        try {
            return jsonMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"text\":\"" + alertHeader + " - " + url + "\"}";
        }
    }
}

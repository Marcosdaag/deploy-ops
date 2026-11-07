package com.marcosdaag.deployops.service;

import com.marcosdaag.deployops.model.IncidentEntity;
import com.marcosdaag.deployops.model.OutboxEventEntity;
import com.marcosdaag.deployops.model.ServiceEntity;
import com.marcosdaag.deployops.model.enums.OutboxEventType;
import com.marcosdaag.deployops.model.enums.OutboxStatus;
import com.marcosdaag.deployops.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private static final Logger logger = LoggerFactory.getLogger(OutboxEventService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEventEntity recordIncidentEvent(ServiceEntity service, IncidentEntity incident, OutboxEventType eventType) {
        String eventId = UUID.randomUUID().toString();
        Long userId = (service.getUser() != null) ? service.getUser().getId() : null;

        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", eventId);
        payload.put("eventType", eventType.name());
        payload.put("occurredAt", LocalDateTime.now().toString());
        payload.put("serviceId", service.getId());
        payload.put("serviceName", service.getName());
        payload.put("url", service.getUrl());
        payload.put("status", service.getStatus().name());
        payload.put("httpStatus", service.getExpectedStatusCode() != null ? service.getExpectedStatusCode() : 0);
        payload.put("incidentId", incident != null ? incident.getId() : null);

        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(payload);
        } catch (Exception e) {
            payloadJson = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType.name() + "\"}";
        }

        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(eventId);
        event.setAggregateType("SERVICE");
        event.setAggregateId(service.getId());
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setPayloadJson(payloadJson);
        event.setStatus(OutboxStatus.PENDING);
        event.setAttemptCount(0);
        event.setCreatedAt(LocalDateTime.now());

        OutboxEventEntity saved = outboxEventRepository.save(event);
        logger.info("Evento Outbox transaccional registrado [ID: {}, Tipo: {}, Servicio: {}]",
                saved.getId(), eventType, service.getName());
        return saved;
    }

    @Transactional
    public OutboxEventEntity recordManualEvent(Long userId, OutboxEventType eventType, Map<String, Object> customDetails) {
        String eventId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>(customDetails != null ? customDetails : Map.of());
        payload.put("eventId", eventId);
        payload.put("eventType", eventType.name());
        payload.put("occurredAt", LocalDateTime.now().toString());

        String payloadJson;
        try {
            payloadJson = jsonMapper.writeValueAsString(payload);
        } catch (Exception e) {
            payloadJson = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType.name() + "\"}";
        }

        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(eventId);
        event.setAggregateType("MANUAL");
        event.setAggregateId(null);
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setPayloadJson(payloadJson);
        event.setStatus(OutboxStatus.PENDING);
        event.setAttemptCount(0);
        event.setCreatedAt(LocalDateTime.now());

        return outboxEventRepository.save(event);
    }
}

package com.marcosdaag.deployops.model.enums;

public enum OutboxEventType {
    INCIDENT_OPENED,
    INCIDENT_RESOLVED,
    WEBHOOK_TEST_PING,
    CHAOS_SIMULATION
}

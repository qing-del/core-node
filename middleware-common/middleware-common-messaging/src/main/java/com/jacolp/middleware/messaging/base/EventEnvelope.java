package com.jacolp.middleware.messaging.base;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;

/** Stable wire envelope shared by all cross-module domain events. */
public record EventEnvelope(
        String eventId,
        String eventType,
        int schemaVersion,
        String aggregateType,
        String aggregateId,
        Instant occurredAt,
        String correlationId,
        JsonNode payload) {

    public EventEnvelope {
        requireText(eventId, "eventId");
        requireText(eventType, "eventType");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(aggregateType, "aggregateType");
        requireText(aggregateId, "aggregateId");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

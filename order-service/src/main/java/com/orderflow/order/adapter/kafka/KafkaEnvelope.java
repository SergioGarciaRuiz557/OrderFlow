package com.orderflow.order.adapter.kafka;

import java.time.Instant;
import java.util.UUID;

/** Stable JSON envelope owned by Order's Kafka adapters, never by its domain model. */
public record KafkaEnvelope<T>(UUID messageId, String messageType, UUID correlationId,
                               UUID causationId, UUID aggregateId, Instant occurredAt,
                               int version, T payload) {
    public KafkaEnvelope {
        if (messageId == null || messageType == null || messageType.isBlank() || correlationId == null
                || aggregateId == null || occurredAt == null || version != 1 || payload == null) {
            throw new IllegalArgumentException("Invalid Kafka message envelope");
        }
    }
}

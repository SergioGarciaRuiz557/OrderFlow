package com.orderflow.order.adapter.kafka;

import java.time.Instant;
import java.util.UUID;

/** Sobre JSON estable que pertenece a los adaptadores de Kafka de Order, nunca a su modelo de dominio. */
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

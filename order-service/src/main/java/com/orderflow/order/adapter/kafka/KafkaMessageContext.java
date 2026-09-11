package com.orderflow.order.adapter.kafka;

import java.util.UUID;
/** Adapter-local metadata scope used while a Kafka callback synchronously produces follow-up messages. */
public final class KafkaMessageContext {
    private static final ThreadLocal<Metadata> CURRENT = new ThreadLocal<>();

    private KafkaMessageContext() { }

    public static Metadata current() { return CURRENT.get(); }

    public static void run(Metadata metadata, Runnable action) {
        Metadata previous = CURRENT.get();
        CURRENT.set(metadata);
        try { action.run(); } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public record Metadata(UUID messageId, UUID correlationId) { }
}

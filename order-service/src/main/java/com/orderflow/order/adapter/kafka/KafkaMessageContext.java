package com.orderflow.order.adapter.kafka;

import java.util.UUID;
/** Ámbito de metadatos local del adaptador utilizado mientras una notificación de Kafka produce mensajes posteriores de forma síncrona. */
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

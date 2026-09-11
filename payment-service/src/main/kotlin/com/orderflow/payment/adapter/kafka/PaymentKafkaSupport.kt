package com.orderflow.payment.adapter.kafka

import java.time.Instant
import java.util.UUID

data class KafkaEnvelope<T>(
    val messageId: UUID,
    val messageType: String,
    val correlationId: UUID,
    val causationId: UUID?,
    val aggregateId: UUID,
    val occurredAt: Instant,
    val version: Int = 1,
    val payload: T,
)

object KafkaMessageContext {
    data class Metadata(val messageId: UUID, val correlationId: UUID)
    private val current = ThreadLocal<Metadata>()
    fun get(): Metadata? = current.get()
    fun run(metadata: Metadata, action: () -> Unit) {
        val previous = current.get(); current.set(metadata)
        try { action() } finally { if (previous == null) current.remove() else current.set(previous) }
    }
}

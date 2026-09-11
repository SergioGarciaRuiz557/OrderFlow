package com.orderflow.inventory.adapter.`out`.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderflow.inventory.adapter.kafka.*
import com.orderflow.inventory.application.port.`out`.ClockProvider
import com.orderflow.inventory.application.port.`out`.InventoryEventPublisher
import com.orderflow.inventory.domain.model.*
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@ConditionalOnProperty(name = ["orderflow.kafka.enabled"], havingValue = "true", matchIfMissing = true)
class KafkaInventoryEventPublisher(
    private val kafka: KafkaTemplate<String, String>,
    private val json: ObjectMapper,
    private val properties: InventoryKafkaProperties,
    private val clock: ClockProvider,
) : InventoryEventPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun inventoryReserved(orderId: OrderId, reservations: List<ReservationResult.Reserved>) {
        val first = reservations.first().reservation
        publish(orderId, "InventoryReservedEvent", ReservedPayload(
            UUID.fromString(orderId.value), first.id.value,
            reservations.map { ReservedItem(it.inventoryItem.productId.value, it.reservation.quantity.value) },
        ))
    }
    override fun inventoryRejected(orderId: OrderId, rejection: ReservationResult.Rejected) = publish(
        orderId, "InventoryRejectedEvent", RejectedPayload(UUID.fromString(orderId.value), rejection.reason.name),
    )
    override fun inventoryReleased(orderId: OrderId, releases: List<ReleaseResult>) {
        val id = releases.firstNotNullOfOrNull {
            when (it) {
                is ReleaseResult.Released -> it.reservation.id.value
                is ReleaseResult.AlreadyReleased -> it.reservation.id.value
                is ReleaseResult.ReservationNotFound -> null
            }
        }
        publish(orderId, "InventoryReleasedEvent", ReleasedPayload(UUID.fromString(orderId.value), id))
    }
    private fun publish(orderId: OrderId, type: String, payload: Any) {
        val orderUuid = UUID.fromString(orderId.value)
        val context = requireNotNull(KafkaMessageContext.get()) { "Inventory events require an inbound message context" }
        val envelope = KafkaEnvelope(UUID.randomUUID(), type, context.correlationId, context.messageId, orderUuid, clock.now(), 1, payload)
        kafka.send(properties.topics.inventoryEvents, orderId.value, json.writeValueAsString(envelope)).whenComplete { _, error ->
            if (error == null) logger.info("Kafka message sent: messageType={}, messageId={}, correlationId={}, orderId={}", type, envelope.messageId, envelope.correlationId, orderId.value)
            else logger.error("Kafka send failed: messageType={}, messageId={}, correlationId={}, orderId={}", type, envelope.messageId, envelope.correlationId, orderId.value, error)
        }
    }
    data class ReservedItem(val productId: String, val quantity: Int)
    data class ReservedPayload(val orderId: UUID, val reservationId: UUID, val reservedItems: List<ReservedItem>)
    data class RejectedPayload(val orderId: UUID, val reason: String)
    data class ReleasedPayload(val orderId: UUID, val reservationId: UUID?)
}

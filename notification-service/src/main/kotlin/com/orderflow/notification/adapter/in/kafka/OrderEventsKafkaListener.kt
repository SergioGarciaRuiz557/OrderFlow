package com.orderflow.notification.adapter.`in`.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.orderflow.notification.application.port.`in`.*
import com.orderflow.notification.domain.model.OrderId
import com.orderflow.notification.domain.model.Recipient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID
import java.time.Instant

@Component
@ConditionalOnProperty(name = ["orderflow.kafka.enabled"], havingValue = "true", matchIfMissing = true)
class OrderEventsKafkaListener(
    private val json: ObjectMapper,
    private val confirmed: SendOrderConfirmedNotificationUseCase,
    private val cancelled: SendOrderCancelledNotificationUseCase,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${orderflow.kafka.topics.order-events}"], groupId = "\${orderflow.kafka.group}")
    fun listen(value: String) {
        val root = runCatching { json.readTree(value) }.getOrElse { throw IllegalArgumentException("Malformed Kafka JSON", it) }
        val messageId = root.uuid("messageId")
        val correlationId = root.uuid("correlationId")
        val aggregateId = root.uuid("aggregateId")
        val type = root.requiredText("messageType")
        require(root.has("causationId")) { "causationId field is required" }
        if (!root.path("causationId").isNull) root.uuid("causationId")
        Instant.parse(root.requiredText("occurredAt"))
        require(root.path("version").asInt() == 1) { "Unsupported contract version" }
        val payload = root.path("payload")
        val orderId = payload.uuid("orderId")
        require(orderId == aggregateId) { "aggregateId must equal payload.orderId" }
        require(orderId == correlationId) { "correlationId must equal payload.orderId" }
        val customerId = payload.uuid("customerId")
        // Customer contact lookup is outside this commit; this deterministic non-routable address
        // adapts the current UUID-only Order contract to Notification's existing recipient port.
        val recipient = Recipient("$customerId@orderflow.invalid")
        logger.info("Kafka message received: messageType={}, messageId={}, correlationId={}, orderId={}", type, messageId, correlationId, orderId)
        when (type) {
            "OrderConfirmedEvent" -> confirmed.send(SendOrderConfirmedNotificationCommand(OrderId(orderId.toString()), recipient))
            "OrderCancelledEvent" -> cancelled.send(SendOrderCancelledNotificationCommand(OrderId(orderId.toString()), recipient))
            else -> throw IllegalArgumentException("Unsupported order event: $type")
        }
    }
    private fun JsonNode.requiredText(field: String): String = path(field).asText().also { require(it.isNotBlank()) { "$field is required" } }
    private fun JsonNode.uuid(field: String): UUID = UUID.fromString(requiredText(field))
}

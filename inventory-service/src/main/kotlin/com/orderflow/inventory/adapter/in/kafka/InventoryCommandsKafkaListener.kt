package com.orderflow.inventory.adapter.`in`.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.orderflow.inventory.adapter.kafka.KafkaMessageContext
import com.orderflow.inventory.application.port.`in`.InventoryMessagingUseCase
import com.orderflow.inventory.application.port.`in`.ReservationItem
import com.orderflow.inventory.application.port.`in`.ReserveOrderInventoryCommand
import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.Quantity
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.UUID
import java.time.Instant

@Component
@ConditionalOnProperty(name = ["orderflow.kafka.enabled"], havingValue = "true", matchIfMissing = true)
class InventoryCommandsKafkaListener(private val json: ObjectMapper, private val useCase: InventoryMessagingUseCase) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${orderflow.kafka.topics.inventory-commands}"], groupId = "\${orderflow.kafka.group}")
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
        require(payload.isObject) { "payload is required" }
        val orderUuid = payload.uuid("orderId")
        require(orderUuid == aggregateId) { "aggregateId must equal payload.orderId" }
        require(orderUuid == correlationId) { "correlationId must equal payload.orderId" }
        val orderId = OrderId(orderUuid.toString())
        logger.info("Kafka message received: messageType={}, messageId={}, correlationId={}, orderId={}", type, messageId, correlationId, orderId.value)
        KafkaMessageContext.run(KafkaMessageContext.Metadata(messageId, correlationId)) {
            when (type) {
                "ReserveInventoryCommand" -> useCase.reserve(
                    ReserveOrderInventoryCommand(orderId, payload.path("items").map { item ->
                        ReservationItem(ProductId(item.requiredText("productId")), Quantity(item.path("quantity").asInt()))
                    }),
                )
                "ReleaseInventoryCommand" -> useCase.release(orderId)
                else -> throw IllegalArgumentException("Unsupported inventory command: $type")
            }
        }
    }

    private fun JsonNode.requiredText(field: String): String = path(field).asText().also { require(it.isNotBlank()) { "$field is required" } }
    private fun JsonNode.uuid(field: String): UUID = UUID.fromString(requiredText(field))
}

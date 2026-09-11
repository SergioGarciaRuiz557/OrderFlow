package com.orderflow.payment.adapter.`in`.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.orderflow.payment.adapter.kafka.KafkaMessageContext
import com.orderflow.payment.application.port.`in`.AuthorizePaymentCommand
import com.orderflow.payment.application.port.`in`.PaymentMessagingUseCase
import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.PaymentMethodId
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.Currency
import java.util.UUID
import java.time.Instant

@Component
@ConditionalOnProperty(name = ["orderflow.kafka.enabled"], havingValue = "true", matchIfMissing = true)
class AuthorizePaymentKafkaListener(private val json: ObjectMapper, private val useCase: PaymentMessagingUseCase) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${orderflow.kafka.topics.payment-commands}"], groupId = "\${orderflow.kafka.group}")
    fun listen(value: String) {
        val root = runCatching { json.readTree(value) }.getOrElse { throw IllegalArgumentException("Malformed Kafka JSON", it) }
        val messageId = root.uuid("messageId")
        val correlationId = root.uuid("correlationId")
        val aggregateId = root.uuid("aggregateId")
        require(root.has("causationId")) { "causationId field is required" }
        if (!root.path("causationId").isNull) root.uuid("causationId")
        Instant.parse(root.requiredText("occurredAt"))
        require(root.requiredText("messageType") == "AuthorizePaymentCommand") { "Unsupported payment command" }
        require(root.path("version").asInt() == 1) { "Unsupported contract version" }
        val payload = root.path("payload")
        val orderId = payload.uuid("orderId")
        require(orderId == aggregateId) { "aggregateId must equal payload.orderId" }
        require(orderId == correlationId) { "correlationId must equal payload.orderId" }
        logger.info("Kafka message received: messageType=AuthorizePaymentCommand, messageId={}, correlationId={}, orderId={}", messageId, correlationId, orderId)
        val command = AuthorizePaymentCommand(
            OrderId(orderId.toString()),
            Money.of(BigDecimal(payload.requiredText("amount")), Currency.getInstance(payload.requiredText("currency"))),
            PaymentMethodId(payload.requiredText("paymentMethodId")),
        )
        KafkaMessageContext.run(KafkaMessageContext.Metadata(messageId, correlationId)) { useCase.authorize(command) }
    }
    private fun JsonNode.requiredText(field: String): String = path(field).asText().also { require(it.isNotBlank()) { "$field is required" } }
    private fun JsonNode.uuid(field: String): UUID = UUID.fromString(requiredText(field))
}

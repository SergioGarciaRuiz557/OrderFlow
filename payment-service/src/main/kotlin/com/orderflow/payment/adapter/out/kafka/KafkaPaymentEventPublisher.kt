package com.orderflow.payment.adapter.`out`.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderflow.payment.adapter.kafka.*
import com.orderflow.payment.application.port.`out`.ClockProvider
import com.orderflow.payment.application.port.`out`.PaymentEventPublisher
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@ConditionalOnProperty(name = ["orderflow.kafka.enabled"], havingValue = "true", matchIfMissing = true)
class KafkaPaymentEventPublisher(
    private val kafka: KafkaTemplate<String, String>,
    private val json: ObjectMapper,
    private val properties: PaymentKafkaProperties,
    private val clock: ClockProvider,
) : PaymentEventPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)
    override fun paymentAuthorized(payment: Payment) = publish(payment, "PaymentAuthorizedEvent", AuthorizedPayload(
        UUID.fromString(payment.orderId.value), payment.id.value, requireNotNull(payment.providerReference).value,
        payment.amount.amount, payment.amount.currency.currencyCode,
    ))
    override fun paymentRejected(payment: Payment, reason: PaymentFailureReason) = publish(payment, "PaymentRejectedEvent", RejectedPayload(
        UUID.fromString(payment.orderId.value), payment.id.value, reason.value,
    ))
    private fun publish(payment: Payment, type: String, payload: Any) {
        val context = requireNotNull(KafkaMessageContext.get()) { "Payment events require an inbound message context" }
        val orderId = UUID.fromString(payment.orderId.value)
        val envelope = KafkaEnvelope(UUID.randomUUID(), type, context.correlationId, context.messageId, orderId, clock.now(), 1, payload)
        kafka.send(properties.topics.paymentEvents, payment.orderId.value, json.writeValueAsString(envelope)).whenComplete { _, error ->
            if (error == null) logger.info("Kafka message sent: messageType={}, messageId={}, correlationId={}, orderId={}", type, envelope.messageId, envelope.correlationId, payment.orderId.value)
            else logger.error("Kafka send failed: messageType={}, messageId={}, correlationId={}, orderId={}", type, envelope.messageId, envelope.correlationId, payment.orderId.value, error)
        }
    }
    data class AuthorizedPayload(val orderId: UUID, val paymentId: UUID, val providerReference: String, val amount: java.math.BigDecimal, val currency: String)
    data class RejectedPayload(val orderId: UUID, val paymentId: UUID?, val reason: String)
}

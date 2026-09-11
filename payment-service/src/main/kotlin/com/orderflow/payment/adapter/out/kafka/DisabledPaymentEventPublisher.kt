package com.orderflow.payment.adapter.`out`.kafka

import com.orderflow.payment.application.port.`out`.PaymentEventPublisher
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["orderflow.kafka.enabled"], havingValue = "false")
class DisabledPaymentEventPublisher : PaymentEventPublisher {
    override fun paymentAuthorized(payment: Payment) = Unit
    override fun paymentRejected(payment: Payment, reason: PaymentFailureReason) = Unit
}

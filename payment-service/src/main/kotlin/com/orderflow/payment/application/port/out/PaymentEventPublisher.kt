package com.orderflow.payment.application.port.`out`

import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason

/** Semantic boundary for definitive payment integration events. */
interface PaymentEventPublisher {
    fun paymentAuthorized(payment: Payment)
    fun paymentRejected(payment: Payment, reason: PaymentFailureReason)
}

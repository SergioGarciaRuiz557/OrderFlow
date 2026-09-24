package com.orderflow.payment.application.port.`out`

import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason

/** Límite semántico para los eventos definitivos de integración de pagos. */
interface PaymentEventPublisher {
    fun paymentAuthorized(payment: Payment)
    fun paymentRejected(payment: Payment, reason: PaymentFailureReason)
}

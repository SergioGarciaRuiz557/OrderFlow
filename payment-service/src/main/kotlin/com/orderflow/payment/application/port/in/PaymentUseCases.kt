package com.orderflow.payment.application.port.`in`

import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentId
import com.orderflow.payment.domain.model.PaymentMethodId

data class AuthorizePaymentCommand(
    val orderId: OrderId,
    val amount: Money,
    val paymentMethodId: PaymentMethodId,
)

sealed interface PaymentAuthorizationResult {
    val payment: Payment
    val alreadyProcessed: Boolean

    data class Authorized(
        override val payment: Payment,
        override val alreadyProcessed: Boolean,
    ) : PaymentAuthorizationResult

    data class Rejected(
        override val payment: Payment,
        val reason: PaymentFailureReason,
        override val alreadyProcessed: Boolean,
    ) : PaymentAuthorizationResult
}

fun interface AuthorizePaymentUseCase {
    fun authorize(command: AuthorizePaymentCommand): PaymentAuthorizationResult
}

fun interface GetPaymentUseCase {
    fun getPayment(paymentId: PaymentId): Payment?
}

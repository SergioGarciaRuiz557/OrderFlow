package com.orderflow.payment.application.port.`out`

import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentId
import com.orderflow.payment.domain.model.PaymentMethodId
import com.orderflow.payment.domain.model.PaymentProviderReference
import java.time.Instant

interface PaymentRepository {
    fun findById(paymentId: PaymentId): Payment?
    fun findByOrderId(orderId: OrderId): Payment?
    fun save(payment: Payment): Payment
}

data class PaymentGatewayRequest(
    val idempotencyKey: OrderId,
    val orderId: OrderId,
    val amount: Money,
    val paymentMethodId: PaymentMethodId,
)

sealed interface GatewayAuthorizationResult {
    data class Authorized(val providerReference: PaymentProviderReference) : GatewayAuthorizationResult
    data class Rejected(val reason: PaymentFailureReason) : GatewayAuthorizationResult
}

fun interface PaymentGateway {
    @Throws(PaymentGatewayException::class)
    fun authorize(request: PaymentGatewayRequest): GatewayAuthorizationResult
}

class PaymentGatewayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun interface ClockProvider {
    fun now(): Instant
}

interface PaymentAuthorizationLock {
    fun <T : Any> withLock(orderId: OrderId, operation: () -> T): T
}

class DuplicatePaymentException(cause: Throwable) : RuntimeException("A payment already exists for the order", cause)

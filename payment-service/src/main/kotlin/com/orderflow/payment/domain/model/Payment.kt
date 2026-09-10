package com.orderflow.payment.domain.model

import java.time.Instant

enum class PaymentStatus {
    PENDING,
    AUTHORIZED,
    REJECTED,
}

@ConsistentCopyVisibility
data class Payment private constructor(
    val id: PaymentId,
    val orderId: OrderId,
    val amount: Money,
    val paymentMethodId: PaymentMethodId,
    val status: PaymentStatus,
    val providerReference: PaymentProviderReference?,
    val failureReason: PaymentFailureReason?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long?,
) {
    init {
        require(!updatedAt.isBefore(createdAt)) { "Payment update time cannot precede creation time" }
        when (status) {
            PaymentStatus.PENDING -> require(providerReference == null && failureReason == null) {
                "Pending payments cannot contain an authorization outcome"
            }
            PaymentStatus.AUTHORIZED -> require(providerReference != null && failureReason == null) {
                "Authorized payments require only a provider reference"
            }
            PaymentStatus.REJECTED -> require(providerReference == null && failureReason != null) {
                "Rejected payments require only a failure reason"
            }
        }
    }

    fun authorize(reference: PaymentProviderReference, at: Instant): Payment {
        check(status == PaymentStatus.PENDING) { "Only a pending payment can be authorized" }
        return copy(
            status = PaymentStatus.AUTHORIZED,
            providerReference = reference,
            updatedAt = at,
        )
    }

    fun reject(reason: PaymentFailureReason, at: Instant): Payment {
        check(status == PaymentStatus.PENDING) { "Only a pending payment can be rejected" }
        return copy(
            status = PaymentStatus.REJECTED,
            failureReason = reason,
            updatedAt = at,
        )
    }

    fun matches(amount: Money, paymentMethodId: PaymentMethodId): Boolean =
        this.amount == amount && this.paymentMethodId == paymentMethodId

    companion object {
        fun pending(
            id: PaymentId,
            orderId: OrderId,
            amount: Money,
            paymentMethodId: PaymentMethodId,
            createdAt: Instant,
        ): Payment = Payment(
            id = id,
            orderId = orderId,
            amount = amount,
            paymentMethodId = paymentMethodId,
            status = PaymentStatus.PENDING,
            providerReference = null,
            failureReason = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            version = null,
        )

        fun reconstitute(
            id: PaymentId,
            orderId: OrderId,
            amount: Money,
            paymentMethodId: PaymentMethodId,
            status: PaymentStatus,
            providerReference: PaymentProviderReference?,
            failureReason: PaymentFailureReason?,
            createdAt: Instant,
            updatedAt: Instant,
            version: Long,
        ): Payment = Payment(
            id,
            orderId,
            amount,
            paymentMethodId,
            status,
            providerReference,
            failureReason,
            createdAt,
            updatedAt,
            version,
        )
    }
}

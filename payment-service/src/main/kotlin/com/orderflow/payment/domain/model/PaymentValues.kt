package com.orderflow.payment.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.UUID

@JvmInline
value class PaymentId(val value: UUID) {
    companion object {
        fun new(): PaymentId = PaymentId(UUID.randomUUID())
    }
}

@JvmInline
value class OrderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Order id must not be blank" }
    }
}

@JvmInline
value class PaymentMethodId(val value: String) {
    init {
        require(value.isNotBlank()) { "Payment method id must not be blank" }
    }
}

@JvmInline
value class PaymentProviderReference(val value: String) {
    init {
        require(value.isNotBlank()) { "Payment provider reference must not be blank" }
    }
}

@JvmInline
value class PaymentFailureReason(val value: String) {
    init {
        require(value.isNotBlank()) { "Payment failure reason must not be blank" }
    }
}

@ConsistentCopyVisibility
data class Money private constructor(
    val amount: BigDecimal,
    val currency: Currency,
) {
    init {
        require(amount.signum() >= 0) { "Payment amount cannot be negative" }
        require(currency == EUR) { "Only EUR is currently supported" }
        require(amount.scale() == SCALE) { "Payment amount must use two decimal places" }
    }

    companion object {
        private const val SCALE = 2
        val EUR: Currency = Currency.getInstance("EUR")

        fun euros(amount: BigDecimal): Money = Money(normalize(amount), EUR)

        fun of(amount: BigDecimal, currency: Currency): Money = Money(normalize(amount), currency)

        private fun normalize(amount: BigDecimal): BigDecimal =
            amount.setScale(SCALE, RoundingMode.UNNECESSARY)
    }
}

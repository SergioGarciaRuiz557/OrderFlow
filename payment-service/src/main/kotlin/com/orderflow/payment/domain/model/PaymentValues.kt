package com.orderflow.payment.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.UUID

/**
 * Strongly typed identifier of a [Payment] aggregate.
 *
 * Wrapping the raw [UUID] prevents payment identifiers from being accidentally exchanged with
 * order identifiers or other strings. `@JvmInline` normally avoids allocating an extra wrapper at
 * runtime while retaining domain-specific type safety in Kotlin APIs.
 *
 * @property value globally unique UUID stored as the `payments.payment_id` primary key.
 */
@JvmInline
value class PaymentId(val value: UUID) {
    /** Factory operations for payment identifiers. */
    companion object {
        /**
         * Creates a new random identifier for a payment that has not yet been persisted.
         *
         * Retries of an existing payment do not call this method: they reload the original aggregate
         * and keep its identifier. Provider idempotency uses [OrderId], so it also remains stable if
         * local persistence fails before a newly generated identifier can be stored.
         */
        fun new(): PaymentId = PaymentId(UUID.randomUUID())
    }
}

/**
 * Identifier assigned by the Order bounded context to the business operation being paid.
 *
 * This is more than a foreign reference: it is the business idempotency key for authorization. A
 * single order can own only one payment row, and the same value is sent to the payment gateway as
 * its idempotency key.
 *
 * @property value external order identifier, preserved without transformation.
 * @throws IllegalArgumentException when [value] is blank.
 */
@JvmInline
value class OrderId(val value: String) {
    /** Rejects identifiers that cannot identify a real order. */
    init {
        require(value.isNotBlank()) { "Order id must not be blank" }
    }
}

/**
 * Opaque identifier of the customer's payment instrument at the provider.
 *
 * The service never parses card or bank details from this value. Keeping the token opaque limits
 * the bounded context to authorization orchestration and avoids storing sensitive instrument data.
 *
 * @property value provider-facing payment-method token.
 * @throws IllegalArgumentException when [value] is blank.
 */
@JvmInline
value class PaymentMethodId(val value: String) {
    /** Ensures every authorization request identifies a payment instrument. */
    init {
        require(value.isNotBlank()) { "Payment method id must not be blank" }
    }
}

/**
 * Reference returned by the payment provider after a successful authorization.
 *
 * The reference is stored for traceability, support investigations, reconciliation, and future
 * capture/refund operations. Domain and database invariants allow it only on `AUTHORIZED` payments.
 *
 * @property value opaque provider authorization reference.
 * @throws IllegalArgumentException when [value] is blank.
 */
@JvmInline
value class PaymentProviderReference(val value: String) {
    /** Prevents successful payments from carrying an unusable provider reference. */
    init {
        require(value.isNotBlank()) { "Payment provider reference must not be blank" }
    }
}

/**
 * Machine-readable reason for a known business rejection.
 *
 * This type is deliberately not used for timeouts, connection errors, or other technical failures;
 * those cross the [com.orderflow.payment.application.port.out.PaymentGatewayException] boundary.
 * Keeping the two concepts separate lets a future Kafka consumer retry technical failures without
 * treating them as card declines.
 *
 * @property value stable reason code supplied or mapped by the gateway adapter.
 * @throws IllegalArgumentException when [value] is blank.
 */
@JvmInline
value class PaymentFailureReason(val value: String) {
    /** Guarantees that every rejected payment explains its business outcome. */
    init {
        require(value.isNotBlank()) { "Payment failure reason must not be blank" }
    }
}

/**
 * Immutable monetary value used by the Payment domain.
 *
 * [BigDecimal] is mandatory for decimal currency arithmetic; binary floating-point types would
 * introduce rounding errors. Construction is private so all values pass through [euros] or [of],
 * which normalize the amount to exactly two decimal places. The first service version accepts EUR
 * only, but currency remains explicit to prevent ambiguous amounts and ease future extension.
 *
 * @property amount non-negative decimal amount normalized to two fractional digits.
 * @property currency ISO-4217 currency; currently it must be EUR.
 * @throws IllegalArgumentException for a negative amount, unsupported currency, or invalid scale.
 * @throws ArithmeticException when normalization would require rounding rather than adding zeros.
 */
@ConsistentCopyVisibility
data class Money private constructor(
    val amount: BigDecimal,
    val currency: Currency,
) {
    /** Revalidates all invariants for every instance, including generated data-class copies. */
    init {
        // `signum` compares numerically and is not affected by BigDecimal scale.
        require(amount.signum() >= 0) { "Payment amount cannot be negative" }
        require(currency == EUR) { "Only EUR is currently supported" }
        require(amount.scale() == SCALE) { "Payment amount must use two decimal places" }
    }

    /** Controlled construction and normalization policy for monetary values. */
    companion object {
        /** Number of decimal places used by EUR and by the PostgreSQL `NUMERIC(19, 2)` column. */
        private const val SCALE = 2

        /** Canonical Java currency instance used for all currently supported payments. */
        val EUR: Currency = Currency.getInstance("EUR")

        /**
         * Creates an EUR amount and normalizes values such as `10` or `10.0` to `10.00`.
         *
         * @param amount decimal amount that must be non-negative and exactly representable at scale 2.
         * @return validated EUR money.
         */
        fun euros(amount: BigDecimal): Money = Money(normalize(amount), EUR)

        /**
         * Reconstructs money when the currency is supplied explicitly, primarily from persistence.
         *
         * @param amount stored or externally supplied decimal amount.
         * @param currency explicit ISO-4217 currency to validate.
         * @return normalized and validated money.
         */
        fun of(amount: BigDecimal, currency: Currency): Money = Money(normalize(amount), currency)

        /**
         * Enforces the common scale without silently changing monetary value.
         *
         * [RoundingMode.UNNECESSARY] accepts `10`, `10.0`, and `10.00`, but rejects `10.001` rather
         * than rounding a business amount behind the caller's back.
         */
        private fun normalize(amount: BigDecimal): BigDecimal =
            amount.setScale(SCALE, RoundingMode.UNNECESSARY)
    }
}

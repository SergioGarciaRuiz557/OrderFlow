package com.orderflow.payment.domain.model

import java.time.Instant

/**
 * Complete and intentionally small lifecycle of a payment authorization.
 *
 * There is no generic status setter. [Payment] behavior controls transitions so terminal outcomes
 * cannot return to `PENDING` or change from rejection to authorization.
 */
enum class PaymentStatus {
    /** Created and persisted, but no definitive provider decision has been applied yet. */
    PENDING,

    /** The provider approved the amount and supplied a traceable authorization reference. */
    AUTHORIZED,

    /** The provider returned a definitive business rejection with a reason. */
    REJECTED,
}

/**
 * Aggregate root of the Payment bounded context.
 *
 * The aggregate owns authorization state and guarantees that provider references, rejection reasons,
 * timestamps, and status always form a coherent snapshot. It is immutable: behavior returns a new
 * [Payment] instead of mutating fields, which makes invalid intermediate states impossible and works
 * naturally with optimistic locking.
 *
 * The primary constructor is private. New business instances must use [pending], while the persistence
 * adapter uses [reconstitute]. Both paths execute the same `init` invariants. `@ConsistentCopyVisibility`
 * ensures the generated data-class `copy` method remains private like the constructor, preventing
 * callers from bypassing lifecycle behavior.
 *
 * @property id technical aggregate identifier and database primary key.
 * @property orderId business idempotency key; only one payment is permitted per order.
 * @property amount explicit, normalized monetary amount requested for authorization.
 * @property paymentMethodId opaque provider payment-method token.
 * @property status current lifecycle state.
 * @property providerReference provider authorization reference, present only when authorized.
 * @property failureReason business rejection reason, present only when rejected.
 * @property createdAt immutable time at which the pending payment was created.
 * @property updatedAt time of the most recent successful domain transition.
 * @property version JPA optimistic-lock token, or `null` before first persistence.
 */
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
    /** Validates invariants shared by newly created, transitioned, and database-loaded snapshots. */
    init {
        // Lifecycle timestamps must be monotonic even if a caller provides a custom ClockProvider.
        require(!updatedAt.isBefore(createdAt)) { "Payment update time cannot precede creation time" }

        // These exhaustive checks make the nullable outcome fields valid only in their logical state.
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

    /**
     * Applies the provider's successful authorization decision.
     *
     * Only a pending payment may be authorized. The returned snapshot contains the provider reference
     * and transition time while retaining immutable request data and persistence version. Calling this
     * method on an authorized or rejected payment is a programming/domain-state error.
     *
     * @param reference non-blank provider reference proving the successful authorization.
     * @param at authoritative transition time supplied by the application clock.
     * @return a new authorized aggregate snapshot.
     * @throws IllegalStateException when this payment is no longer pending.
     * @throws IllegalArgumentException when [at] precedes [createdAt].
     */
    fun authorize(reference: PaymentProviderReference, at: Instant): Payment {
        // Guarding before `copy` prevents terminal-state transitions and duplicate authorization.
        check(status == PaymentStatus.PENDING) { "Only a pending payment can be authorized" }
        return copy(
            status = PaymentStatus.AUTHORIZED,
            providerReference = reference,
            updatedAt = at,
        )
    }

    /**
     * Applies a definitive business rejection returned by the provider.
     *
     * Technical failures must never call this method; they leave the persisted payment pending and
     * propagate as an exception so infrastructure can retry. Like authorization, rejection is a
     * terminal transition and can occur only once.
     *
     * @param reason non-blank business reason explaining the provider decision.
     * @param at authoritative transition time supplied by the application clock.
     * @return a new rejected aggregate snapshot.
     * @throws IllegalStateException when this payment is no longer pending.
     * @throws IllegalArgumentException when [at] precedes [createdAt].
     */
    fun reject(reason: PaymentFailureReason, at: Instant): Payment {
        check(status == PaymentStatus.PENDING) { "Only a pending payment can be rejected" }
        return copy(
            status = PaymentStatus.REJECTED,
            failureReason = reason,
            updatedAt = at,
        )
    }

    /**
     * Determines whether an incoming retry represents the exact original business request.
     *
     * Order identity alone finds the idempotency record; amount and payment method must also match.
     * A changed request is a conflict rather than a harmless retry and is rejected by the application.
     *
     * @param amount amount supplied by the repeated command.
     * @param paymentMethodId payment instrument supplied by the repeated command.
     * @return `true` only when both immutable request attributes match.
     */
    fun matches(amount: Money, paymentMethodId: PaymentMethodId): Boolean =
        this.amount == amount && this.paymentMethodId == paymentMethodId

    /** Controlled construction paths for new and persisted aggregate snapshots. */
    companion object {
        /**
         * Creates a new payment before any definitive provider outcome exists.
         *
         * Outcome fields begin empty, both timestamps share the creation time, and [version] remains
         * `null` until JPA inserts the row. The application persists this state before it interprets a
         * technical gateway failure, enabling a later retry to locate the same business operation.
         *
         * @return a valid, not-yet-persisted pending aggregate.
         */
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

        /**
         * Rebuilds a payment from trusted persistence primitives through domain value objects.
         *
         * This is not a back door around invariants: the private constructor and `init` block validate
         * lifecycle consistency and timestamps. A non-null version is required because this path is
         * exclusively for rows that already exist in PostgreSQL.
         *
         * @return immutable domain representation of one persisted payment row.
         * @throws IllegalArgumentException when stored state violates an aggregate invariant.
         */
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

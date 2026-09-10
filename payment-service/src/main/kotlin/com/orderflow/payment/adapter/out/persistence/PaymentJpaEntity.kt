package com.orderflow.payment.adapter.`out`.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Mutable JPA representation of one row in the `payments` table.
 *
 * This class exists only in the persistence adapter. Hibernate requires a no-argument-compatible
 * constructor, mutable properties, and mapping annotations, so the entity is deliberately separate
 * from the immutable [com.orderflow.payment.domain.model.Payment] aggregate. Application and domain
 * code must never use this type directly.
 *
 * Constructor defaults are infrastructure placeholders used by Hibernate's generated no-arg
 * constructor; real writes are populated by [PaymentPersistenceMapper]. Database `NOT NULL`, check,
 * and uniqueness constraints remain the final persistence safety net.
 *
 * @property paymentId UUID primary key; immutable after insertion.
 * @property orderId unique business authorization key; immutable after insertion.
 * @property amount decimal amount stored at precision 19 and scale 2.
 * @property currency explicit three-character ISO currency code.
 * @property paymentMethodId opaque payment-instrument token fixed for the payment request.
 * @property status persisted name of the domain lifecycle state.
 * @property providerReference provider reference, nullable except for authorized state.
 * @property failureReason business rejection code, nullable except for rejected state.
 * @property createdAt immutable creation instant.
 * @property updatedAt latest lifecycle transition instant.
 * @property version Hibernate optimistic-lock token; `null` marks a new entity.
 */
@Entity
@Table(name = "payments")
class PaymentJpaEntity(
    // `@Id` maps the aggregate identity; `updatable = false` prevents accidental key replacement.
    @Id
    @Column(name = "payment_id", nullable = false, updatable = false)
    var paymentId: UUID = UUID.randomUUID(),

    // The migration also declares a UNIQUE constraint because this is the business idempotency key.
    @Column(name = "order_id", nullable = false, updatable = false)
    var orderId: String = "",

    // JPA precision and scale mirror PostgreSQL NUMERIC(19, 2) and the Money normalization policy.
    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO.setScale(2),

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    var currency: String = "EUR",

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    var paymentMethodId: String = "",

    // Strings keep JPA storage decoupled from enum ordinal ordering; the mapper validates the name.
    @Column(name = "status", nullable = false)
    var status: String = "",

    @Column(name = "provider_reference")
    var providerReference: String? = null,

    @Column(name = "failure_reason")
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.EPOCH,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,

    // Hibernate includes this value in UPDATE predicates and detects stale concurrent snapshots.
    @Version
    @Column(name = "version", nullable = false)
    var version: Long? = null,
)

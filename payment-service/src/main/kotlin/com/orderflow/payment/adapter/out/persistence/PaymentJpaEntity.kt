package com.orderflow.payment.adapter.`out`.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payments")
class PaymentJpaEntity(
    @Id
    @Column(name = "payment_id", nullable = false, updatable = false)
    var paymentId: UUID = UUID.randomUUID(),

    @Column(name = "order_id", nullable = false, updatable = false)
    var orderId: String = "",

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO.setScale(2),

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    var currency: String = "EUR",

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    var paymentMethodId: String = "",

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

    @Version
    @Column(name = "version", nullable = false)
    var version: Long? = null,
)

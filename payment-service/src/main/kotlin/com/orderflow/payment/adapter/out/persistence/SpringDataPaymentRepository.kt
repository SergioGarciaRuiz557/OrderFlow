package com.orderflow.payment.adapter.`out`.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Internal Spring Data repository for [PaymentJpaEntity].
 *
 * Extending [JpaRepository] provides entity-level CRUD and flushing operations to
 * [JpaPaymentRepositoryAdapter]. This interface belongs strictly to the outbound adapter; exposing it
 * as an application port would couple the core architecture to Spring Data and mutable JPA entities.
 */
interface SpringDataPaymentRepository : JpaRepository<PaymentJpaEntity, UUID> {
    /**
     * Derives a query for the database-unique order business key.
     *
     * @param orderId raw string stored in `payments.order_id`.
     * @return matching entity or `null`; the unique constraint guarantees at most one row.
     */
    fun findByOrderId(orderId: String): PaymentJpaEntity?
}

package com.orderflow.payment.adapter.`out`.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataPaymentRepository : JpaRepository<PaymentJpaEntity, UUID> {
    fun findByOrderId(orderId: String): PaymentJpaEntity?
}

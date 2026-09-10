package com.orderflow.payment.application.service

import com.orderflow.payment.application.port.`in`.GetPaymentUseCase
import com.orderflow.payment.application.port.`out`.PaymentRepository
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentId
import org.springframework.stereotype.Service

/**
 * Read-only application service for retrieving payment aggregate snapshots.
 *
 * The service deliberately exposes the [GetPaymentUseCase] port and delegates storage access to the
 * framework-independent [PaymentRepository]. It contains no CRUD mutation behavior and does not
 * expose Spring Data types to callers.
 *
 * @property repository outbound persistence port used for lookup.
 */
@Service
class GetPaymentService(private val repository: PaymentRepository) : GetPaymentUseCase {
    /**
     * Retrieves the latest persisted payment without altering state.
     *
     * @param paymentId aggregate primary key to locate.
     * @return payment snapshot, or `null` when no such identifier exists.
     */
    override fun getPayment(paymentId: PaymentId): Payment? = repository.findById(paymentId)
}

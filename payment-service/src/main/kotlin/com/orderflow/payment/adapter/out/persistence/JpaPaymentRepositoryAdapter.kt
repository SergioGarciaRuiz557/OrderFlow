package com.orderflow.payment.adapter.`out`.persistence

import com.orderflow.payment.application.port.`out`.DuplicatePaymentException
import com.orderflow.payment.application.port.`out`.PaymentRepository
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JpaPaymentRepositoryAdapter(
    private val repository: SpringDataPaymentRepository,
    private val mapper: PaymentPersistenceMapper,
) : PaymentRepository {

    @Transactional(readOnly = true)
    override fun findById(paymentId: PaymentId): Payment? =
        repository.findById(paymentId.value).orElse(null)?.let(mapper::toDomain)

    @Transactional(readOnly = true)
    override fun findByOrderId(orderId: OrderId): Payment? =
        repository.findByOrderId(orderId.value)?.let(mapper::toDomain)

    @Transactional
    override fun save(payment: Payment): Payment = try {
        mapper.toDomain(repository.saveAndFlush(mapper.toEntity(payment)))
    } catch (exception: DataIntegrityViolationException) {
        throw DuplicatePaymentException(exception)
    }
}

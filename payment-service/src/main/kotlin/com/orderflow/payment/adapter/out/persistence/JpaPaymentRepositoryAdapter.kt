package com.orderflow.payment.adapter.`out`.persistence

import com.orderflow.payment.application.port.`out`.DuplicatePaymentException
import com.orderflow.payment.application.port.`out`.PaymentRepository
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * PostgreSQL/JPA implementation of the framework-independent [PaymentRepository] output port.
 *
 * The adapter owns persistence transaction declarations and all conversion between domain aggregates
 * and JPA entities. Application callers see only [Payment], [PaymentId], and [OrderId]. `saveAndFlush`
 * is used deliberately so optimistic-lock and database-constraint failures occur inside this adapter,
 * where infrastructure exceptions can be translated before they cross the hexagonal boundary.
 *
 * When called from [PostgreSqlPaymentAuthorizationLock], these methods join its existing transaction.
 * When called independently, each annotation creates the appropriate read-only or read-write scope.
 *
 * @property repository internal Spring Data entity repository.
 * @property mapper explicit translator between persistence and domain representations.
 */
@Repository
class JpaPaymentRepositoryAdapter(
    private val repository: SpringDataPaymentRepository,
    private val mapper: PaymentPersistenceMapper,
) : PaymentRepository {

    /**
     * Loads a payment by primary key in a read-only transaction.
     *
     * `Optional.orElse(null)` converts Spring Data's representation into the nullable convention of
     * the core port; mapping runs only when an entity exists.
     *
     * @param paymentId strongly typed domain identifier unwrapped for the JPA query.
     * @return reconstructed aggregate or `null` when no row exists.
     */
    @Transactional(readOnly = true)
    override fun findById(paymentId: PaymentId): Payment? =
        repository.findById(paymentId.value).orElse(null)?.let(mapper::toDomain)

    /**
     * Loads the single payment associated with an order's business operation.
     *
     * @param orderId business idempotency key unwrapped for the derived Spring Data query.
     * @return reconstructed aggregate or `null` before the order's first authorization request.
     */
    @Transactional(readOnly = true)
    override fun findByOrderId(orderId: OrderId): Payment? =
        repository.findByOrderId(orderId.value)?.let(mapper::toDomain)

    /**
     * Inserts a new payment or optimistically updates an existing payment snapshot.
     *
     * The domain is converted to a detached entity, `saveAndFlush` forces SQL execution immediately,
     * and the resulting entity is mapped back so callers receive the database-assigned version. The
     * database unique constraints remain authoritative under concurrency even if application checks
     * were bypassed.
     *
     * @param payment validated aggregate snapshot.
     * @return persisted snapshot carrying the new optimistic-lock version.
     * @throws DuplicatePaymentException when PostgreSQL rejects a uniqueness/integrity conflict.
     */
    @Transactional
    override fun save(payment: Payment): Payment = try {
        // Flushing here keeps constraint errors inside this translation boundary.
        mapper.toDomain(repository.saveAndFlush(mapper.toEntity(payment)))
    } catch (exception: DataIntegrityViolationException) {
        // Core callers do not need to know which Spring exception represented the database failure.
        throw DuplicatePaymentException(exception)
    }
}

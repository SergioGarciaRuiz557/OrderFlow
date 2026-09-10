package com.orderflow.payment.adapter.`out`.persistence

import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentId
import com.orderflow.payment.domain.model.PaymentMethodId
import com.orderflow.payment.domain.model.PaymentProviderReference
import com.orderflow.payment.domain.model.PaymentStatus
import org.springframework.stereotype.Component
import java.util.Currency

/**
 * Explicit bidirectional translator between JPA storage and the Payment domain aggregate.
 *
 * Mapping is kept in one adapter component instead of annotating domain classes. Converting database
 * primitives into value objects re-applies domain validation, so corrupt or incompatible rows fail
 * fast during reconstruction. The reverse conversion unwraps domain types only at the infrastructure
 * boundary where Hibernate needs mutable primitives.
 */
@Component
class PaymentPersistenceMapper {
    /**
     * Reconstitutes a validated immutable aggregate from a persisted entity.
     *
     * Nullable columns are converted with `?.let`, preserving their absence without unsafe casts.
     * Status names use [PaymentStatus.valueOf], intentionally failing on unknown database values.
     * [Payment.reconstitute] then verifies cross-field lifecycle and timestamp invariants.
     *
     * @param entity Hibernate entity loaded from the `payments` table.
     * @return validated domain snapshot.
     * @throws IllegalArgumentException for invalid identifiers, currency, status, amount, or lifecycle.
     * @throws IllegalStateException when a supposedly persisted row has no optimistic-lock version.
     */
    fun toDomain(entity: PaymentJpaEntity): Payment = Payment.reconstitute(
        // Primitive storage values regain their domain-specific types one field at a time.
        id = PaymentId(entity.paymentId),
        orderId = OrderId(entity.orderId),
        amount = Money.of(entity.amount, Currency.getInstance(entity.currency)),
        paymentMethodId = PaymentMethodId(entity.paymentMethodId),
        status = PaymentStatus.valueOf(entity.status),
        // References and reasons are legitimately absent in states where the domain forbids them.
        providerReference = entity.providerReference?.let(::PaymentProviderReference),
        failureReason = entity.failureReason?.let(::PaymentFailureReason),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        version = requireNotNull(entity.version) { "Persisted payment must have a version" },
    )

    /**
     * Converts an immutable aggregate into a detached entity suitable for `save` or `merge`.
     *
     * The aggregate version is preserved: `null` tells Hibernate to insert, while a concrete version
     * enables optimistic checking during merge. No business validation occurs here because [Payment]
     * already guarantees coherent state.
     *
     * @param payment aggregate snapshot to persist.
     * @return technology-specific mutable entity containing equivalent values.
     */
    fun toEntity(payment: Payment): PaymentJpaEntity = PaymentJpaEntity(
        // Inline/value objects are unwrapped only at this outermost persistence boundary.
        paymentId = payment.id.value,
        orderId = payment.orderId.value,
        amount = payment.amount.amount,
        currency = payment.amount.currency.currencyCode,
        paymentMethodId = payment.paymentMethodId.value,
        status = payment.status.name,
        providerReference = payment.providerReference?.value,
        failureReason = payment.failureReason?.value,
        createdAt = payment.createdAt,
        updatedAt = payment.updatedAt,
        version = payment.version,
    )
}

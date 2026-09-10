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

@Component
class PaymentPersistenceMapper {
    fun toDomain(entity: PaymentJpaEntity): Payment = Payment.reconstitute(
        id = PaymentId(entity.paymentId),
        orderId = OrderId(entity.orderId),
        amount = Money.of(entity.amount, Currency.getInstance(entity.currency)),
        paymentMethodId = PaymentMethodId(entity.paymentMethodId),
        status = PaymentStatus.valueOf(entity.status),
        providerReference = entity.providerReference?.let(::PaymentProviderReference),
        failureReason = entity.failureReason?.let(::PaymentFailureReason),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        version = requireNotNull(entity.version) { "Persisted payment must have a version" },
    )

    fun toEntity(payment: Payment): PaymentJpaEntity = PaymentJpaEntity(
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

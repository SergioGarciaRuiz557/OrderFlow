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
 * Traductor bidireccional explícito entre el almacenamiento JPA y el agregado del dominio de Pagos.
 *
 * La asignación se mantiene en un único componente adaptador, en lugar de anotar las clases de
 * dominio. Convertir las primitivas de la base de datos en objetos de valor vuelve a aplicar la
 * validación del dominio, por lo que las filas dañadas o incompatibles fallan pronto durante la
 * reconstrucción. La conversión inversa solo desenvuelve los tipos del dominio en el límite de
 * infraestructura donde Hibernate necesita primitivas mutables.
 */
@Component
class PaymentPersistenceMapper {
    /**
     * Reconstituye un agregado inmutable validado a partir de una entidad conservada.
     *
     * Las columnas anulables se convierten con `?.let`, conservando su ausencia sin conversiones de
     * tipo inseguras. Los nombres de estado usan [PaymentStatus.valueOf] y fallan intencionadamente
     * ante valores desconocidos de la base de datos. Después, [Payment.reconstitute] verifica los
     * invariantes entre campos del ciclo de vida y de las marcas de tiempo.
     *
     * @param entity entidad de Hibernate cargada desde la tabla `payments`.
     * @return instantánea validada del dominio.
     * @throws IllegalArgumentException ante identificadores, moneda, estado, importe o ciclo de vida no válidos.
     * @throws IllegalStateException cuando una fila supuestamente conservada carece de versión de bloqueo optimista.
     */
    fun toDomain(entity: PaymentJpaEntity): Payment = Payment.reconstitute(
        // Los valores primitivos de almacenamiento recuperan sus tipos específicos del dominio campo a campo.
        id = PaymentId(entity.paymentId),
        orderId = OrderId(entity.orderId),
        amount = Money.of(entity.amount, Currency.getInstance(entity.currency)),
        paymentMethodId = PaymentMethodId(entity.paymentMethodId),
        status = PaymentStatus.valueOf(entity.status),
        // Las referencias y los motivos están legítimamente ausentes en los estados en los que el dominio los prohíbe.
        providerReference = entity.providerReference?.let(::PaymentProviderReference),
        failureReason = entity.failureReason?.let(::PaymentFailureReason),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        version = requireNotNull(entity.version) { "Persisted payment must have a version" },
    )

    /**
     * Convierte un agregado inmutable en una entidad separada apta para `save` o `merge`.
     *
     * Se conserva la versión del agregado: `null` indica a Hibernate que inserte, mientras que una
     * versión concreta habilita la comprobación optimista durante la fusión. Aquí no se realiza ninguna
     * validación de negocio porque [Payment] ya garantiza un estado coherente.
     *
     * @param payment instantánea del agregado que se debe conservar.
     * @return entidad mutable específica de la tecnología que contiene valores equivalentes.
     */
    fun toEntity(payment: Payment): PaymentJpaEntity = PaymentJpaEntity(
        // Los objetos inline o de valor solo se desenvuelven en este límite exterior de persistencia.
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

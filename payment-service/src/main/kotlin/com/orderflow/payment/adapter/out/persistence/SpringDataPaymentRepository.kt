package com.orderflow.payment.adapter.`out`.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Repositorio interno de Spring Data para [PaymentJpaEntity].
 *
 * Extender [JpaRepository] proporciona a [JpaPaymentRepositoryAdapter] operaciones CRUD y de vaciado
 * en el nivel de entidad. Esta interfaz pertenece estrictamente al adaptador de salida; exponerla como
 * puerto de aplicación acoplaría la arquitectura central a Spring Data y a entidades JPA mutables.
 */
interface SpringDataPaymentRepository : JpaRepository<PaymentJpaEntity, UUID> {
    /**
     * Deriva una consulta para la clave de negocio del pedido, única en la base de datos.
     *
     * @param orderId cadena sin procesar almacenada en `payments.order_id`.
     * @return entidad coincidente o `null`; la restricción única garantiza como máximo una fila.
     */
    fun findByOrderId(orderId: String): PaymentJpaEntity?
}

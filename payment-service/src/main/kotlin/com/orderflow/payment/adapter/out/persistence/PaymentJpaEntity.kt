package com.orderflow.payment.adapter.`out`.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Representación JPA mutable de una fila de la tabla `payments`.
 *
 * Esta clase solo existe en el adaptador de persistencia. Hibernate requiere un constructor compatible
 * sin argumentos, propiedades mutables y anotaciones de asignación, por lo que la entidad está
 * deliberadamente separada del agregado inmutable [com.orderflow.payment.domain.model.Payment]. El
 * código de aplicación y dominio nunca debe usar directamente este tipo.
 *
 * Los valores predeterminados del constructor son marcadores de infraestructura que usa el constructor
 * sin argumentos generado por Hibernate; [PaymentPersistenceMapper] rellena las escrituras reales.
 * Las restricciones `NOT NULL`, de comprobación y de unicidad de la base de datos siguen siendo la
 * última red de seguridad de la persistencia.
 *
 * @property paymentId clave primaria UUID, inmutable tras la inserción.
 * @property orderId clave única de autorización de negocio, inmutable tras la inserción.
 * @property amount importe decimal almacenado con precisión 19 y escala 2.
 * @property currency código de moneda ISO explícito de tres caracteres.
 * @property paymentMethodId token opaco del instrumento de pago fijado para la solicitud de pago.
 * @property status nombre conservado del estado del ciclo de vida del dominio.
 * @property providerReference referencia del proveedor, anulable salvo en el estado autorizado.
 * @property failureReason código de rechazo de negocio, anulable salvo en el estado rechazado.
 * @property createdAt instante inmutable de creación.
 * @property updatedAt instante de la última transición del ciclo de vida.
 * @property version token de bloqueo optimista de Hibernate; `null` señala una entidad nueva.
 */
@Entity
@Table(name = "payments")
class PaymentJpaEntity(
    // `@Id` asigna la identidad del agregado; `updatable = false` evita la sustitución accidental de la clave.
    @Id
    @Column(name = "payment_id", nullable = false, updatable = false)
    var paymentId: UUID = UUID.randomUUID(),

    // La migración también declara una restricción UNIQUE porque esta es la clave de idempotencia de negocio.
    @Column(name = "order_id", nullable = false, updatable = false)
    var orderId: String = "",

    // La precisión y escala de JPA reflejan NUMERIC(19, 2) de PostgreSQL y la política de normalización de Money.
    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO.setScale(2),

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    var currency: String = "EUR",

    @Column(name = "payment_method_id", nullable = false, updatable = false)
    var paymentMethodId: String = "",

    // Las cadenas desacoplan el almacenamiento JPA del orden ordinal del enum; el asignador valida el nombre.
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

    // Hibernate incluye este valor en los predicados UPDATE y detecta instantáneas concurrentes obsoletas.
    @Version
    @Column(name = "version", nullable = false)
    var version: Long? = null,
)

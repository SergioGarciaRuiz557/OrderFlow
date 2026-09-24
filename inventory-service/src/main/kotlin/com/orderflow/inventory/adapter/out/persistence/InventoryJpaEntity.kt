package com.orderflow.inventory.adapter.`out`.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * Representación JPA de la tabla `inventory_items`.
 *
 * Esta clase mutable solo existe en el adaptador de persistencia. Está separada deliberadamente del
 * agregado inmutable [com.orderflow.inventory.domain.model.InventoryItem] para que los requisitos de
 * Hibernate sobre proxies, constructores sin argumentos y propiedades mutables no den forma al modelo de dominio.
 *
 * @property productId clave primaria asignada al producto; nunca cambia después de la inserción.
 * @property availableQuantity existencias persistidas disponibles para nuevas reservas.
 * @property version token de bloqueo optimista de Hibernate. `null` identifica una entidad nueva;
 * PostgreSQL e Hibernate asignan la primera versión durante la inserción.
 * @property reservations filas hijas que pertenecen a este agregado de persistencia. Las cascadas
 * hacen que los cambios de existencias y reservas formen parte de la misma operación de persistencia,
 * y la eliminación de huérfanos refleja la propiedad.
 */
@Entity
@Table(name = "inventory_items")
class InventoryItemJpaEntity(
    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    var productId: String = "",

    @Column(name = "available_quantity", nullable = false)
    var availableQuantity: Int = 0,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long? = null,

    @OneToMany(mappedBy = "inventoryItem", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var reservations: MutableList<StockReservationJpaEntity> = mutableListOf(),
)

/**
 * Representación JPA de la tabla `stock_reservations`.
 *
 * Los valores que definen la identidad y la intención de la reserva son inmutables en el nivel de
 * mapeo de la base de datos; solo pueden cambiar el estado del ciclo de vida y el instante de
 * liberación. La validación del dominio sigue realizándose en el modelo de dominio independiente
 * cuando se reconstruyen las filas.
 *
 * @property reservationId UUID que actúa como clave primaria de la fila de reserva.
 * @property orderId clave de idempotencia de negocio del pedido dentro de un producto.
 * @property quantity unidades asignadas por esta reserva.
 * @property status nombre persistido del estado de reserva del dominio.
 * @property reservedAt marca temporal inmutable de aceptación.
 * @property releasedAt marca temporal de liberación, o `null` para una fila activa.
 */
@Entity
@Table(name = "stock_reservations")
class StockReservationJpaEntity(
    @Id
    @Column(name = "reservation_id", nullable = false, updatable = false)
    var reservationId: UUID = UUID.randomUUID(),

    @Column(name = "order_id", nullable = false, updatable = false)
    var orderId: String = "",

    @Column(name = "quantity", nullable = false, updatable = false)
    var quantity: Int = 0,

    @Column(name = "status", nullable = false)
    var status: String = "",

    @Column(name = "reserved_at", nullable = false, updatable = false)
    var reservedAt: Instant = Instant.EPOCH,

    @Column(name = "released_at")
    var releasedAt: Instant? = null,
) {
    /**
     * Entidad de persistencia de inventario propietaria.
     *
     * La asociación escribe la clave externa `product_id`. La carga diferida evita volver a cargar
     * la entidad padre cuando ya se accede a una hija desde un agregado recuperado de forma inmediata.
     * El mapeador asigna esta propiedad `lateinit` antes de persistir un nuevo grafo de entidades;
     * Hibernate la asigna al hidratar las filas de la base de datos.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    lateinit var inventoryItem: InventoryItemJpaEntity
}

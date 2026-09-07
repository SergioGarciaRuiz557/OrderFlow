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
 * JPA representation of the `inventory_items` table.
 *
 * This mutable class exists only in the persistence adapter. It is deliberately separate from the
 * immutable [com.orderflow.inventory.domain.model.InventoryItem] aggregate so Hibernate's proxy,
 * no-argument-constructor, and mutable-property requirements do not shape the domain model.
 *
 * @property productId assigned product primary key; it never changes after insertion.
 * @property availableQuantity persisted stock available to new reservations.
 * @property version Hibernate optimistic-lock token. `null` identifies a new entity; PostgreSQL and
 * Hibernate assign the first version during insertion.
 * @property reservations child rows owned by this persistence aggregate. Cascades make stock and
 * reservation changes part of the same persistence operation, and orphan removal mirrors ownership.
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
 * JPA representation of the `stock_reservations` table.
 *
 * Values that define reservation identity and intent are immutable at the database-mapping level;
 * only lifecycle status and release time can change. Domain validation still occurs in the separate
 * domain model when rows are reconstructed.
 *
 * @property reservationId UUID primary key of the reservation row.
 * @property orderId order-level business idempotency key within a product.
 * @property quantity units allocated by this reservation.
 * @property status persisted name of the domain reservation status.
 * @property reservedAt immutable acceptance timestamp.
 * @property releasedAt release timestamp, or `null` for an active row.
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
     * Owning inventory persistence entity.
     *
     * The association writes the `product_id` foreign key. Lazy loading avoids loading the parent
     * again when a child is already traversed from an eagerly fetched aggregate. The mapper assigns
     * this `lateinit` property before a new entity graph is persisted; Hibernate assigns it when
     * hydrating database rows.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    lateinit var inventoryItem: InventoryItemJpaEntity
}

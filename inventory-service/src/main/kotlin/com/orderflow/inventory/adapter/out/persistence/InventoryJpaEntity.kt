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
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    lateinit var inventoryItem: InventoryItemJpaEntity
}

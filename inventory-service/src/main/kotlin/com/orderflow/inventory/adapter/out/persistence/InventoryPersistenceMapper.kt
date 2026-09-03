package com.orderflow.inventory.adapter.`out`.persistence

import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.Quantity
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.ReservationStatus
import com.orderflow.inventory.domain.model.StockReservation
import org.springframework.stereotype.Component

/**
 * Explicit bidirectional mapper between JPA entities and the inventory domain aggregate.
 *
 * The mapping is intentionally not delegated to reflection or generated bean mapping: constructing
 * domain value objects re-applies their invariants, while constructing JPA entities handles the
 * mutable parent/child relationship Hibernate requires. This class is the only place that needs to
 * understand both representations.
 */
@Component
class InventoryPersistenceMapper {
    /**
     * Reconstitutes a validated domain aggregate from a fully loaded JPA entity graph.
     *
     * Every primitive identifier and quantity becomes its corresponding domain value object. Status
     * names are converted to the domain enum, and the persistence version is carried into the
     * aggregate for the next optimistic update.
     *
     * @param entity inventory row with its complete reservation collection loaded.
     * @return immutable domain aggregate representing the persisted snapshot.
     * @throws IllegalArgumentException if persisted values violate domain invariants or contain an
     * unknown reservation status.
     * @throws IllegalStateException if a supposedly persisted entity has no version.
     */
    fun toDomain(entity: InventoryItemJpaEntity): InventoryItem = InventoryItem.reconstitute(
        productId = ProductId(entity.productId),
        availableQuantity = entity.availableQuantity,
        reservations = entity.reservations.map { reservation ->
            StockReservation(
                id = ReservationId(reservation.reservationId),
                orderId = OrderId(reservation.orderId),
                quantity = Quantity(reservation.quantity),
                status = ReservationStatus.valueOf(reservation.status),
                reservedAt = reservation.reservedAt,
                releasedAt = reservation.releasedAt,
            )
        },
        version = requireNotNull(entity.version) { "Persisted inventory must have a version" },
    )

    /**
     * Builds a JPA entity graph from an immutable domain aggregate.
     *
     * The aggregate version is preserved so Hibernate can detect stale updates. Each child entity is
     * linked back to the newly created parent, establishing the owning foreign-key relationship
     * before `saveAndFlush` executes.
     *
     * @param inventoryItem aggregate snapshot to persist.
     * @return detached JPA graph suitable for insertion or optimistic merge.
     */
    fun toEntity(inventoryItem: InventoryItem): InventoryItemJpaEntity {
        // The parent must exist first because each reservation entity references this exact instance.
        val entity = InventoryItemJpaEntity(
            productId = inventoryItem.productId.value,
            availableQuantity = inventoryItem.availableQuantity,
            version = inventoryItem.version,
        )
        // Mapping the complete list ensures aggregate state, not individual child rows, is persisted.
        entity.reservations = inventoryItem.reservations.map { reservation ->
            StockReservationJpaEntity(
                reservationId = reservation.id.value,
                orderId = reservation.orderId.value,
                quantity = reservation.quantity.value,
                status = reservation.status.name,
                reservedAt = reservation.reservedAt,
                releasedAt = reservation.releasedAt,
            ).also { it.inventoryItem = entity }
        }.toMutableList()
        return entity
    }
}

package com.orderflow.inventory.adapter.`out`.persistence

import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.Quantity
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.ReservationStatus
import com.orderflow.inventory.domain.model.StockReservation
import org.springframework.stereotype.Component

@Component
class InventoryPersistenceMapper {
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

    fun toEntity(inventoryItem: InventoryItem): InventoryItemJpaEntity {
        val entity = InventoryItemJpaEntity(
            productId = inventoryItem.productId.value,
            availableQuantity = inventoryItem.availableQuantity,
            version = inventoryItem.version,
        )
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

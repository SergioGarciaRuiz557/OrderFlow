package com.orderflow.inventory.application.port.`out`

import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.ReservationId

interface InventoryRepository {
    fun findByProductId(productId: ProductId): InventoryItem?
    fun findByReservationId(reservationId: ReservationId): InventoryItem?
    fun save(inventoryItem: InventoryItem): InventoryItem
}

class ConcurrentInventoryModificationException(cause: Throwable? = null) :
    RuntimeException("Inventory was modified concurrently", cause)

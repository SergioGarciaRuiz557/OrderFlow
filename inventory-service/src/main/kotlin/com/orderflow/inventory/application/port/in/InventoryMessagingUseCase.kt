package com.orderflow.inventory.application.port.`in`

import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.Quantity

data class ReserveOrderInventoryCommand(val orderId: OrderId, val items: List<ReservationItem>) {
    init { require(items.isNotEmpty()) { "Reservation items are required" } }
}
data class ReservationItem(val productId: ProductId, val quantity: Quantity)

interface InventoryMessagingUseCase {
    fun reserve(command: ReserveOrderInventoryCommand)
    fun release(orderId: OrderId)
}

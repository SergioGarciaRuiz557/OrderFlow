package com.orderflow.inventory.application.port.`in`

import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.Quantity
import com.orderflow.inventory.domain.model.ReleaseResult
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.ReservationResult

data class ReserveInventoryCommand(
    val productId: ProductId,
    val orderId: OrderId,
    val quantity: Quantity,
)

fun interface ReserveInventoryUseCase {
    fun reserve(command: ReserveInventoryCommand): ReservationResult
}

fun interface ReleaseInventoryUseCase {
    fun release(reservationId: ReservationId): ReleaseResult
}

fun interface GetInventoryUseCase {
    fun get(productId: ProductId): InventoryItem?
}

fun interface CreateOrUpdateInventoryUseCase {
    fun setAvailableQuantity(productId: ProductId, quantity: Int): InventoryItem
}

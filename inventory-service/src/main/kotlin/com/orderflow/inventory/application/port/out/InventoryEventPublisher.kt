package com.orderflow.inventory.application.port.`out`

import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ReleaseResult
import com.orderflow.inventory.domain.model.ReservationResult

/** Semantic boundary for publishing definitive Inventory outcomes. */
interface InventoryEventPublisher {
    fun inventoryReserved(orderId: OrderId, reservations: List<ReservationResult.Reserved>)
    fun inventoryRejected(orderId: OrderId, rejection: ReservationResult.Rejected)
    fun inventoryReleased(orderId: OrderId, releases: List<ReleaseResult>)
}

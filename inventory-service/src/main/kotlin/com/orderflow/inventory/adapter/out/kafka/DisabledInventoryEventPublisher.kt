package com.orderflow.inventory.adapter.`out`.kafka

import com.orderflow.inventory.application.port.`out`.InventoryEventPublisher
import com.orderflow.inventory.domain.model.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["orderflow.kafka.enabled"], havingValue = "false")
class DisabledInventoryEventPublisher : InventoryEventPublisher {
    override fun inventoryReserved(orderId: OrderId, reservations: List<ReservationResult.Reserved>) = Unit
    override fun inventoryRejected(orderId: OrderId, rejection: ReservationResult.Rejected) = Unit
    override fun inventoryReleased(orderId: OrderId, releases: List<ReleaseResult>) = Unit
}

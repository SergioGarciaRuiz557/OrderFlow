package com.orderflow.inventory.application.service

import com.orderflow.inventory.application.port.`in`.*
import com.orderflow.inventory.application.port.`out`.InventoryEventPublisher
import com.orderflow.inventory.application.port.`out`.InventoryRepository
import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ReservationResult
import org.springframework.stereotype.Service

@Service
class InventoryMessagingService(
    private val reserveInventory: ReserveInventoryUseCase,
    private val releaseInventory: ReleaseInventoryUseCase,
    private val repository: InventoryRepository,
    private val events: InventoryEventPublisher,
) : InventoryMessagingUseCase {
    override fun reserve(command: ReserveOrderInventoryCommand) {
        val accepted = mutableListOf<ReservationResult.Reserved>()
        for (item in command.items) {
            when (val result = reserveInventory.reserve(ReserveInventoryCommand(item.productId, command.orderId, item.quantity))) {
                is ReservationResult.Reserved -> accepted += result
                is ReservationResult.Rejected -> {
                    // El dominio actual reserva por producto. Repone los elementos anteriores si se
                    // rechaza uno posterior, para que el comando del pedido nunca informe del rechazo
                    // dejando existencias asignadas por este mismo intento.
                    accepted.forEach { releaseInventory.release(it.reservation.id) }
                    events.inventoryRejected(command.orderId, result)
                    return
                }
            }
        }
        events.inventoryReserved(command.orderId, accepted)
    }

    override fun release(orderId: OrderId) {
        val results = repository.findByOrderId(orderId).flatMap { item ->
            item.reservations.filter { it.orderId == orderId }.map { releaseInventory.release(it.id) }
        }
        require(results.isNotEmpty()) { "No inventory reservation exists for order ${orderId.value}" }
        events.inventoryReleased(orderId, results)
    }
}

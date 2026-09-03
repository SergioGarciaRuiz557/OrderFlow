package com.orderflow.inventory.application.service

import com.orderflow.inventory.application.port.`in`.CreateOrUpdateInventoryUseCase
import com.orderflow.inventory.application.port.`in`.GetInventoryUseCase
import com.orderflow.inventory.application.port.`in`.ReleaseInventoryUseCase
import com.orderflow.inventory.application.port.`in`.ReserveInventoryCommand
import com.orderflow.inventory.application.port.`in`.ReserveInventoryUseCase
import com.orderflow.inventory.application.port.`out`.ClockProvider
import com.orderflow.inventory.application.port.`out`.ConcurrentInventoryModificationException
import com.orderflow.inventory.application.port.`out`.InventoryRepository
import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.ReleaseResult
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.ReservationRejectionReason
import com.orderflow.inventory.domain.model.ReservationResult
import org.springframework.stereotype.Service

@Service
class InventoryApplicationService(
    private val inventoryRepository: InventoryRepository,
    private val clockProvider: ClockProvider,
) : ReserveInventoryUseCase,
    ReleaseInventoryUseCase,
    GetInventoryUseCase,
    CreateOrUpdateInventoryUseCase {

    override fun reserve(command: ReserveInventoryCommand): ReservationResult = retryOnConflict {
        val inventoryItem = inventoryRepository.findByProductId(command.productId)
            ?: return@retryOnConflict ReservationResult.Rejected(
                reason = ReservationRejectionReason.INVENTORY_ITEM_NOT_FOUND,
                availableQuantity = null,
            )

        when (val result = inventoryItem.reserve(
            reservationId = ReservationId.new(),
            orderId = command.orderId,
            quantity = command.quantity,
            at = clockProvider.now(),
        )) {
            is ReservationResult.Rejected -> result
            is ReservationResult.Reserved -> {
                if (result.wasAlreadyReserved) {
                    result
                } else {
                    val saved = inventoryRepository.save(result.inventoryItem)
                    result.copy(inventoryItem = saved)
                }
            }
        }
    }

    override fun release(reservationId: ReservationId): ReleaseResult = retryOnConflict {
        val inventoryItem = inventoryRepository.findByReservationId(reservationId)
            ?: return@retryOnConflict ReleaseResult.ReservationNotFound(reservationId)

        when (val result = inventoryItem.release(reservationId, clockProvider.now())) {
            is ReleaseResult.ReservationNotFound -> result
            is ReleaseResult.AlreadyReleased -> result
            is ReleaseResult.Released -> {
                val saved = inventoryRepository.save(result.inventoryItem)
                result.copy(inventoryItem = saved)
            }
        }
    }

    override fun get(productId: ProductId): InventoryItem? = inventoryRepository.findByProductId(productId)

    override fun setAvailableQuantity(productId: ProductId, quantity: Int): InventoryItem {
        require(quantity >= 0) { "Available stock cannot be negative" }
        return retryOnConflict {
            val inventoryItem = inventoryRepository.findByProductId(productId)
                ?.setAvailableQuantity(quantity)
                ?: InventoryItem.create(productId, quantity)
            inventoryRepository.save(inventoryItem)
        }
    }

    private fun <T> retryOnConflict(operation: () -> T): T {
        repeat(MAX_CONCURRENCY_ATTEMPTS - 1) {
            try {
                return operation()
            } catch (_: ConcurrentInventoryModificationException) {
                // Reload the aggregate and re-evaluate its invariants.
            }
        }
        return operation()
    }

    private companion object {
        const val MAX_CONCURRENCY_ATTEMPTS = 3
    }
}

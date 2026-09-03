package com.orderflow.inventory.domain.model

import java.time.Instant

/** Aggregate root for stock and every reservation made against one product. */
@ConsistentCopyVisibility
data class InventoryItem private constructor(
    val productId: ProductId,
    val availableQuantity: Int,
    val reservations: List<StockReservation>,
    val version: Long?,
) {
    init {
        require(availableQuantity >= 0) { "Available stock cannot be negative" }
        require(reservations.distinctBy { it.orderId }.size == reservations.size) {
            "An order may have only one reservation for a product"
        }
    }

    fun setAvailableQuantity(quantity: Int): InventoryItem {
        require(quantity >= 0) { "Available stock cannot be negative" }
        return copy(availableQuantity = quantity)
    }

    fun reserve(
        reservationId: ReservationId,
        orderId: OrderId,
        quantity: Quantity,
        at: Instant,
    ): ReservationResult {
        val existing = reservations.firstOrNull { it.orderId == orderId }
        if (existing != null) {
            return if (existing.status == ReservationStatus.ACTIVE && existing.quantity == quantity) {
                ReservationResult.Reserved(this, existing, wasAlreadyReserved = true)
            } else {
                ReservationResult.Rejected(
                    reason = ReservationRejectionReason.ORDER_ALREADY_HAS_RESERVATION,
                    availableQuantity = availableQuantity,
                )
            }
        }

        if (quantity.value > availableQuantity) {
            return ReservationResult.Rejected(
                reason = ReservationRejectionReason.INSUFFICIENT_STOCK,
                availableQuantity = availableQuantity,
            )
        }

        val reservation = StockReservation.active(reservationId, orderId, quantity, at)
        return ReservationResult.Reserved(
            inventoryItem = copy(
                availableQuantity = availableQuantity - quantity.value,
                reservations = reservations + reservation,
            ),
            reservation = reservation,
            wasAlreadyReserved = false,
        )
    }

    fun release(reservationId: ReservationId, at: Instant): ReleaseResult {
        val reservation = reservations.firstOrNull { it.id == reservationId }
            ?: return ReleaseResult.ReservationNotFound(reservationId)

        if (reservation.status == ReservationStatus.RELEASED) {
            return ReleaseResult.AlreadyReleased(this, reservation)
        }

        val released = reservation.release(at)
        return ReleaseResult.Released(
            inventoryItem = copy(
                availableQuantity = availableQuantity + reservation.quantity.value,
                reservations = reservations.map { if (it.id == reservationId) released else it },
            ),
            reservation = released,
        )
    }

    companion object {
        fun create(productId: ProductId, availableQuantity: Int): InventoryItem = InventoryItem(
            productId = productId,
            availableQuantity = availableQuantity,
            reservations = emptyList(),
            version = null,
        )

        fun reconstitute(
            productId: ProductId,
            availableQuantity: Int,
            reservations: List<StockReservation>,
            version: Long,
        ): InventoryItem = InventoryItem(productId, availableQuantity, reservations.toList(), version)
    }
}

enum class ReservationRejectionReason {
    INSUFFICIENT_STOCK,
    ORDER_ALREADY_HAS_RESERVATION,
    INVENTORY_ITEM_NOT_FOUND,
}

sealed interface ReservationResult {
    data class Reserved(
        val inventoryItem: InventoryItem,
        val reservation: StockReservation,
        val wasAlreadyReserved: Boolean,
    ) : ReservationResult

    data class Rejected(
        val reason: ReservationRejectionReason,
        val availableQuantity: Int?,
    ) : ReservationResult
}

sealed interface ReleaseResult {
    data class Released(
        val inventoryItem: InventoryItem,
        val reservation: StockReservation,
    ) : ReleaseResult

    data class AlreadyReleased(
        val inventoryItem: InventoryItem,
        val reservation: StockReservation,
    ) : ReleaseResult

    data class ReservationNotFound(val reservationId: ReservationId) : ReleaseResult
}

package com.orderflow.inventory.domain.model

import java.time.Instant

enum class ReservationStatus {
    ACTIVE,
    RELEASED,
}

data class StockReservation(
    val id: ReservationId,
    val orderId: OrderId,
    val quantity: Quantity,
    val status: ReservationStatus,
    val reservedAt: Instant,
    val releasedAt: Instant?,
) {
    init {
        require((status == ReservationStatus.ACTIVE) == (releasedAt == null)) {
            "Only released reservations may have a release time"
        }
    }

    fun release(at: Instant): StockReservation = when (status) {
        ReservationStatus.ACTIVE -> copy(status = ReservationStatus.RELEASED, releasedAt = at)
        ReservationStatus.RELEASED -> this
    }

    companion object {
        fun active(
            id: ReservationId,
            orderId: OrderId,
            quantity: Quantity,
            reservedAt: Instant,
        ): StockReservation = StockReservation(
            id = id,
            orderId = orderId,
            quantity = quantity,
            status = ReservationStatus.ACTIVE,
            reservedAt = reservedAt,
            releasedAt = null,
        )
    }
}

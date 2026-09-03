package com.orderflow.inventory.domain.model

import java.util.UUID

@JvmInline
value class ProductId(val value: String) {
    init {
        require(value.isNotBlank()) { "Product id must not be blank" }
    }
}

@JvmInline
value class OrderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Order id must not be blank" }
    }
}

@JvmInline
value class ReservationId(val value: UUID) {
    companion object {
        fun new(): ReservationId = ReservationId(UUID.randomUUID())
    }
}

@JvmInline
value class Quantity(val value: Int) {
    init {
        require(value > 0) { "Reservation quantity must be positive" }
    }
}

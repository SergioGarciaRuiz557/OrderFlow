package com.orderflow.inventory.domain.model

import java.util.UUID

/**
 * Strongly typed identifier of a product whose stock is managed by this bounded context.
 *
 * A value class prevents a product identifier from being accidentally exchanged with another
 * string-based identifier while normally avoiding an additional allocation at runtime.
 *
 * @property value external product identifier used by REST, persistence, and future messages.
 * @throws IllegalArgumentException when [value] is blank.
 */
@JvmInline
value class ProductId(val value: String) {
    /** Validates the identifier at the boundary of the domain model. */
    init {
        require(value.isNotBlank()) { "Product id must not be blank" }
    }
}

/**
 * Strongly typed identifier of the order requesting a stock reservation.
 *
 * The inventory aggregate uses this identifier as its business idempotency key: a given order may
 * create at most one reservation for a product.
 *
 * @property value identifier assigned by the Order bounded context.
 * @throws IllegalArgumentException when [value] is blank.
 */
@JvmInline
value class OrderId(val value: String) {
    /** Rejects identifiers that cannot identify a real order. */
    init {
        require(value.isNotBlank()) { "Order id must not be blank" }
    }
}

/**
 * Globally unique identifier of a [StockReservation].
 *
 * Wrapping [UUID] makes APIs explicit about the kind of identifier they accept and prevents mixing
 * reservation identifiers with product or order identifiers.
 *
 * @property value UUID persisted as the primary key of the reservation record.
 */
@JvmInline
value class ReservationId(val value: UUID) {
    /** Factory operations for reservation identifiers. */
    companion object {
        /**
         * Creates a fresh random identifier for a new reservation attempt.
         *
         * Existing idempotent reservations retain their original identifier; a generated value is
         * used only if the aggregate accepts the attempt as a genuinely new reservation.
         */
        fun new(): ReservationId = ReservationId(UUID.randomUUID())
    }
}

/**
 * Positive number of product units requested by a reservation.
 *
 * Zero and negative quantities are invalid by construction, so aggregate operations do not need to
 * repeat that check. Available stock is represented separately as an `Int` because zero is valid
 * for a stock level but not for a reservation request.
 *
 * @property value number of units to reserve.
 * @throws IllegalArgumentException when [value] is zero or negative.
 */
@JvmInline
value class Quantity(val value: Int) {
    /** Enforces the positive-reservation invariant when the value enters the domain. */
    init {
        require(value > 0) { "Reservation quantity must be positive" }
    }
}

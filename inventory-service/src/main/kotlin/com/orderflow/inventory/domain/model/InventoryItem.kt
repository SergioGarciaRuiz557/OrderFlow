package com.orderflow.inventory.domain.model

import java.time.Instant

/**
 * Aggregate root that owns the stock and reservation history of one product.
 *
 * All stock arithmetic happens through this type. Callers cannot independently decrement stock or
 * mutate a [StockReservation], which keeps availability and reservation history consistent. Domain
 * operations return new aggregate instances so previously loaded state remains an immutable
 * snapshot suitable for optimistic concurrency control.
 *
 * The constructor is private to force creation through [create] or persistence reconstruction
 * through [reconstitute]. Both routes execute the aggregate invariants in the `init` block.
 *
 * @property productId product represented by this aggregate.
 * @property availableQuantity units currently available for new reservations; always non-negative.
 * @property reservations immutable history of active and released reservations for the product.
 * @property version persistence concurrency token, or `null` before the aggregate is first stored.
 */
@ConsistentCopyVisibility
data class InventoryItem private constructor(
    val productId: ProductId,
    val availableQuantity: Int,
    val reservations: List<StockReservation>,
    val version: Long?,
) {
    /**
     * Validates invariants that must hold for every aggregate state, including database-loaded data.
     *
     * The second condition makes the product/order pair the business idempotency boundary. A
     * released reservation remains in history, so the same order cannot later create another
     * reservation for the same product by accident.
     */
    init {
        require(availableQuantity >= 0) { "Available stock cannot be negative" }
        require(reservations.distinctBy { it.orderId }.size == reservations.size) {
            "An order may have only one reservation for a product"
        }
    }

    /**
     * Replaces the stock currently available for new reservations.
     *
     * This operation supports the administration/development API. It changes availability directly
     * but deliberately preserves reservation history. Persisting the returned aggregate uses the
     * existing [version], so concurrent changes are still detected.
     *
     * @param quantity new number of units available for reservation; zero is valid.
     * @return a new aggregate with the requested availability.
     * @throws IllegalArgumentException when [quantity] is negative.
     */
    fun setAvailableQuantity(quantity: Int): InventoryItem {
        require(quantity >= 0) { "Available stock cannot be negative" }
        return copy(availableQuantity = quantity)
    }

    /**
     * Attempts to allocate stock to an order while enforcing availability and idempotency.
     *
     * Evaluation order is intentional:
     * 1. Existing order reservations are handled before stock checks, allowing an exact duplicate
     *    request to return its original successful reservation even if stock is now exhausted.
     * 2. A different request for the same order is rejected to prevent duplicate allocations.
     * 3. New requests are rejected when the requested quantity exceeds availability.
     * 4. Accepted requests atomically reduce availability and append their reservation record in the
     *    returned aggregate state.
     *
     * No `null` value or exception represents a normal business rejection; every path returns an
     * explicit [ReservationResult].
     *
     * @param reservationId candidate identifier for a new reservation.
     * @param orderId order requesting the stock.
     * @param quantity positive number of units requested.
     * @param at authoritative time used if a new reservation is accepted.
     * @return [ReservationResult.Reserved] for new or idempotently repeated success, otherwise
     * [ReservationResult.Rejected] with a business reason.
     */
    fun reserve(
        reservationId: ReservationId,
        orderId: OrderId,
        quantity: Quantity,
        at: Instant,
    ): ReservationResult {
        // Order identity is the first guard because it defines business-level idempotency.
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

        // Stock is checked before subtraction, so a negative state can never be constructed.
        if (quantity.value > availableQuantity) {
            return ReservationResult.Rejected(
                reason = ReservationRejectionReason.INSUFFICIENT_STOCK,
                availableQuantity = availableQuantity,
            )
        }

        // Availability and traceability change together in the new aggregate snapshot.
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

    /**
     * Releases a reservation and restores its units exactly once.
     *
     * Unknown identifiers and repeated releases are different explicit outcomes. This distinction
     * lets inbound adapters define an idempotency policy without hiding malformed commands. For an
     * active reservation, both the reservation status and available stock are updated in one new
     * aggregate state.
     *
     * @param reservationId identifier of the reservation to compensate.
     * @param at authoritative time recorded for a first release.
     * @return one exhaustive [ReleaseResult] describing release, duplicate release, or absence.
     */
    fun release(reservationId: ReservationId, at: Instant): ReleaseResult {
        val reservation = reservations.firstOrNull { it.id == reservationId }
            ?: return ReleaseResult.ReservationNotFound(reservationId)

        if (reservation.status == ReservationStatus.RELEASED) {
            return ReleaseResult.AlreadyReleased(this, reservation)
        }

        // The map replaces only the owned entity being released and preserves the full history.
        val released = reservation.release(at)
        return ReleaseResult.Released(
            inventoryItem = copy(
                availableQuantity = availableQuantity + reservation.quantity.value,
                reservations = reservations.map { if (it.id == reservationId) released else it },
            ),
            reservation = released,
        )
    }

    /** Controlled construction paths for new and persisted aggregate instances. */
    companion object {
        /**
         * Creates inventory that has not yet been persisted and has no reservation history.
         *
         * @param productId product to manage.
         * @param availableQuantity initial stock; zero is permitted.
         * @return a new aggregate whose [version] is `null` until persistence assigns one.
         */
        fun create(productId: ProductId, availableQuantity: Int): InventoryItem = InventoryItem(
            productId = productId,
            availableQuantity = availableQuantity,
            reservations = emptyList(),
            version = null,
        )

        /**
         * Rebuilds an aggregate from persistence without exposing a public unrestricted constructor.
         *
         * A defensive list copy prevents a mutable persistence collection from becoming part of the
         * domain state. Constructor invariants validate the stored data during reconstruction.
         *
         * @param productId persisted product identifier.
         * @param availableQuantity persisted available stock.
         * @param reservations complete persisted reservation history for the product.
         * @param version optimistic-lock version read from PostgreSQL.
         * @return the reconstructed aggregate snapshot.
         */
        fun reconstitute(
            productId: ProductId,
            availableQuantity: Int,
            reservations: List<StockReservation>,
            version: Long,
        ): InventoryItem = InventoryItem(productId, availableQuantity, reservations.toList(), version)
    }
}

/** Business reasons for rejecting an inventory reservation request. */
enum class ReservationRejectionReason {
    /** The requested quantity is greater than the aggregate's current availability. */
    INSUFFICIENT_STOCK,

    /** The order already owns a reservation that is not the same idempotent request. */
    ORDER_ALREADY_HAS_RESERVATION,

    /** No inventory aggregate has been prepared for the requested product. */
    INVENTORY_ITEM_NOT_FOUND,
}

/**
 * Exhaustive business outcome of a reservation attempt.
 *
 * This sealed hierarchy ensures callers must consciously process accepted and rejected outcomes.
 * Technical persistence failures are not included because they are not business rejections.
 */
sealed interface ReservationResult {
    /**
     * Successful reservation outcome.
     *
     * @property inventoryItem aggregate state containing the accepted reservation.
     * @property reservation newly created or previously existing idempotent reservation.
     * @property wasAlreadyReserved `true` when no state change or database write is required.
     */
    data class Reserved(
        val inventoryItem: InventoryItem,
        val reservation: StockReservation,
        val wasAlreadyReserved: Boolean,
    ) : ReservationResult

    /**
     * Expected business rejection that leaves aggregate state unchanged.
     *
     * @property reason rule that prevented allocation.
     * @property availableQuantity stock observed during evaluation, or `null` when the product does
     * not exist and therefore has no stock level.
     */
    data class Rejected(
        val reason: ReservationRejectionReason,
        val availableQuantity: Int?,
    ) : ReservationResult
}

/**
 * Exhaustive business outcome of releasing a reservation.
 *
 * Repeated and unknown releases are modeled separately so application adapters can be idempotent
 * without silently accepting an invalid reservation identifier.
 */
sealed interface ReleaseResult {
    /**
     * First successful release.
     *
     * @property inventoryItem aggregate after stock restoration.
     * @property reservation reservation transitioned to released state.
     */
    data class Released(
        val inventoryItem: InventoryItem,
        val reservation: StockReservation,
    ) : ReleaseResult

    /**
     * Idempotent result for a reservation that was released previously.
     *
     * @property inventoryItem unchanged aggregate state.
     * @property reservation existing released reservation with its original release time.
     */
    data class AlreadyReleased(
        val inventoryItem: InventoryItem,
        val reservation: StockReservation,
    ) : ReleaseResult

    /**
     * Explicit result when no aggregate contains the supplied reservation identifier.
     *
     * @property reservationId identifier that could not be located.
     */
    data class ReservationNotFound(val reservationId: ReservationId) : ReleaseResult
}

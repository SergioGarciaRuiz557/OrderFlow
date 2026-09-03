package com.orderflow.inventory.application.port.`out`

import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.ReservationId

/**
 * Outbound persistence port for complete [InventoryItem] aggregates.
 *
 * The contract uses only domain types, keeping Spring Data and JPA outside the application core.
 * Implementations must load the complete reservation collection because its contents participate in
 * aggregate invariants and order-level idempotency decisions.
 */
interface InventoryRepository {
    /**
     * Loads the aggregate belonging to [productId].
     *
     * @return the complete aggregate, or `null` when no inventory has been prepared.
     */
    fun findByProductId(productId: ProductId): InventoryItem?

    /**
     * Loads the aggregate that owns [reservationId].
     *
     * This lookup supports release commands, which identify a reservation rather than a product.
     *
     * @return the owning aggregate with its complete reservation history, or `null` if unknown.
     */
    fun findByReservationId(reservationId: ReservationId): InventoryItem?

    /**
     * Persists a complete aggregate using its optimistic-lock version.
     *
     * @param inventoryItem new aggregate snapshot to insert or update.
     * @return persisted snapshot containing the database-assigned version.
     * @throws ConcurrentInventoryModificationException if another writer changed conflicting state.
     */
    fun save(inventoryItem: InventoryItem): InventoryItem
}

/**
 * Technology-neutral signal that an aggregate write lost a concurrency race.
 *
 * Persistence adapters translate framework-specific optimistic-lock and relevant uniqueness
 * exceptions into this type. The application service can then reload the latest aggregate and
 * re-evaluate business rules without depending on Spring Data exception classes.
 *
 * @param cause original infrastructure exception, retained for diagnostics.
 */
class ConcurrentInventoryModificationException(cause: Throwable? = null) :
    RuntimeException("Inventory was modified concurrently", cause)

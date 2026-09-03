package com.orderflow.inventory.application.port.`in`

import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.Quantity
import com.orderflow.inventory.domain.model.ReleaseResult
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.ReservationResult

/**
 * Transport-independent input required to request a stock reservation.
 *
 * Future REST or Kafka adapters can construct this command without introducing their transport
 * types into the application or domain layers.
 *
 * @property productId product whose available stock should be allocated.
 * @property orderId order receiving the allocation and serving as the idempotency key.
 * @property quantity positive number of units requested.
 */
data class ReserveInventoryCommand(
    val productId: ProductId,
    val orderId: OrderId,
    val quantity: Quantity,
)

/**
 * Inbound application port for allocating product stock to an order.
 *
 * The port exposes an explicit domain outcome and does not assume whether the caller is an HTTP
 * controller, a test, or a future Kafka consumer.
 */
fun interface ReserveInventoryUseCase {
    /**
     * Attempts to reserve the stock described by [command].
     *
     * @param command validated domain identifiers and positive requested quantity.
     * @return an explicit accepted or rejected [ReservationResult].
     */
    fun reserve(command: ReserveInventoryCommand): ReservationResult
}

/** Inbound application port for compensating a previously accepted reservation. */
fun interface ReleaseInventoryUseCase {
    /**
     * Releases the identified reservation if it is active.
     *
     * @param reservationId reservation to locate across inventory aggregates.
     * @return an explicit result distinguishing release, duplicate release, and unknown identifier.
     */
    fun release(reservationId: ReservationId): ReleaseResult
}

/** Inbound query port for retrieving the current state of one inventory aggregate. */
fun interface GetInventoryUseCase {
    /**
     * Looks up inventory by product.
     *
     * @param productId product whose stock and reservations are requested.
     * @return the aggregate snapshot, or `null` when inventory has not been created for the product.
     */
    fun get(productId: ProductId): InventoryItem?
}

/** Inbound administrative port for preparing or correcting available stock. */
fun interface CreateOrUpdateInventoryUseCase {
    /**
     * Creates inventory when absent or replaces the available quantity when present.
     *
     * Existing reservation history is retained. This operation is intended for administration and
     * development preparation rather than order-driven reservation processing.
     *
     * @param productId product whose inventory is being prepared.
     * @param quantity new available stock; must be zero or greater.
     * @return persisted aggregate including its optimistic-lock version.
     */
    fun setAvailableQuantity(productId: ProductId, quantity: Int): InventoryItem
}

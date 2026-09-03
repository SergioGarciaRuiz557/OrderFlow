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

/**
 * Application service coordinating every Inventory use case.
 *
 * This class is deliberately thin: repositories locate and persist aggregates, [ClockProvider]
 * supplies deterministic timestamps, and [InventoryItem] owns stock arithmetic and business rules.
 * The service additionally provides a bounded retry policy for optimistic concurrency conflicts.
 * Retrying reloads current state and invokes domain behavior again, so the final response reflects
 * the latest stock rather than blindly replaying a stale write.
 *
 * @property inventoryRepository framework-independent access to persisted inventory aggregates.
 * @property clockProvider source of reservation and release timestamps.
 */
@Service
class InventoryApplicationService(
    private val inventoryRepository: InventoryRepository,
    private val clockProvider: ClockProvider,
) : ReserveInventoryUseCase,
    ReleaseInventoryUseCase,
    GetInventoryUseCase,
    CreateOrUpdateInventoryUseCase {

    /**
     * Loads inventory, delegates reservation rules to the aggregate, and persists accepted changes.
     *
     * A missing product is a business rejection. Exact duplicate active requests return their
     * existing reservation without writing again. New accepted reservations are saved, while all
     * rejected results pass through unchanged. The sealed `when` keeps outcome processing exhaustive.
     *
     * @param command product, order, and quantity to reserve.
     * @return accepted or rejected domain result based on the latest available aggregate state.
     */
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
            // Business rejection does not alter state and therefore requires no persistence call.
            is ReservationResult.Rejected -> result
            is ReservationResult.Reserved -> {
                if (result.wasAlreadyReserved) {
                    // An exact duplicate has already been persisted; returning it is the idempotent path.
                    result
                } else {
                    // Saving the whole aggregate atomically persists stock and reservation history.
                    val saved = inventoryRepository.save(result.inventoryItem)
                    result.copy(inventoryItem = saved)
                }
            }
        }
    }

    /**
     * Locates and releases a reservation, persisting stock restoration only on its first release.
     *
     * Both unknown and already released reservations remain visible to the caller. Only a genuine
     * [ReleaseResult.Released] transition produces a database write.
     *
     * @param reservationId reservation requested for compensation.
     * @return exhaustive release outcome evaluated against current persisted state.
     */
    override fun release(reservationId: ReservationId): ReleaseResult = retryOnConflict {
        val inventoryItem = inventoryRepository.findByReservationId(reservationId)
            ?: return@retryOnConflict ReleaseResult.ReservationNotFound(reservationId)

        when (val result = inventoryItem.release(reservationId, clockProvider.now())) {
            // These two outcomes are state preserving, so neither should increment the JPA version.
            is ReleaseResult.ReservationNotFound -> result
            is ReleaseResult.AlreadyReleased -> result
            is ReleaseResult.Released -> {
                val saved = inventoryRepository.save(result.inventoryItem)
                result.copy(inventoryItem = saved)
            }
        }
    }

    /**
     * Returns the current aggregate snapshot for administrative inspection.
     *
     * @param productId product being queried.
     * @return persisted inventory or `null` if it does not exist.
     */
    override fun get(productId: ProductId): InventoryItem? = inventoryRepository.findByProductId(productId)

    /**
     * Creates inventory or replaces its currently available stock.
     *
     * Validation occurs before repository access so invalid negative values fail fast. Existing
     * aggregates preserve their reservations; absent products begin with empty reservation history.
     * Concurrency conflicts cause the current aggregate to be reloaded before applying the quantity.
     *
     * @param productId product being initialized or adjusted.
     * @param quantity new available quantity; zero is valid.
     * @return saved aggregate with its current persistence version.
     * @throws IllegalArgumentException when [quantity] is negative.
     */
    override fun setAvailableQuantity(productId: ProductId, quantity: Int): InventoryItem {
        require(quantity >= 0) { "Available stock cannot be negative" }
        return retryOnConflict {
            val inventoryItem = inventoryRepository.findByProductId(productId)
                ?.setAvailableQuantity(quantity)
                ?: InventoryItem.create(productId, quantity)
            inventoryRepository.save(inventoryItem)
        }
    }

    /**
     * Executes [operation] with a small bounded optimistic-concurrency retry policy.
     *
     * Each retry reruns the complete lambda, including repository reads and domain evaluation. This
     * is essential for correctness: after another reservation consumes the last unit, a retry must
     * observe that state and return insufficient stock instead of forcing the stale allocation.
     * The final attempt is allowed to propagate its exception, preventing an unbounded loop during
     * sustained contention or an incorrectly classified infrastructure failure.
     *
     * @param operation complete read/evaluate/write unit to retry.
     * @return the first successful operation result.
     * @throws ConcurrentInventoryModificationException if every attempt conflicts.
     */
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

    /** Internal concurrency policy constants. */
    private companion object {
        /** Maximum number of complete attempts before the final conflict is propagated. */
        const val MAX_CONCURRENCY_ATTEMPTS = 3
    }
}

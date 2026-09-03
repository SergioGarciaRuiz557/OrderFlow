package com.orderflow.inventory.adapter.`out`.persistence

import com.orderflow.inventory.application.port.`out`.ConcurrentInventoryModificationException
import com.orderflow.inventory.application.port.`out`.InventoryRepository
import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.ReservationId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * PostgreSQL/JPA implementation of the framework-independent [InventoryRepository] port.
 *
 * The adapter owns transaction boundaries for individual persistence operations, delegates query
 * execution to Spring Data, and converts entity graphs through [InventoryPersistenceMapper]. It also
 * translates framework exceptions into the application's concurrency vocabulary so higher layers do
 * not depend on Spring Data.
 *
 * @property repository technology-specific Spring Data repository.
 * @property mapper explicit translator between persistence and domain representations.
 */
@Repository
class JpaInventoryRepositoryAdapter(
    private val repository: SpringDataInventoryRepository,
    private val mapper: InventoryPersistenceMapper,
) : InventoryRepository {

    /**
     * Loads a complete inventory aggregate by product in a read-only transaction.
     *
     * @param productId domain identifier converted to the database string key.
     * @return mapped domain aggregate, or `null` when no row exists.
     */
    @Transactional(readOnly = true)
    override fun findByProductId(productId: ProductId): InventoryItem? =
        repository.findAggregateByProductId(productId.value)?.let(mapper::toDomain)

    /**
     * Finds the aggregate that owns a reservation in a read-only transaction.
     *
     * @param reservationId domain identifier converted to the UUID database key.
     * @return complete owning aggregate, or `null` when the reservation is unknown.
     */
    @Transactional(readOnly = true)
    override fun findByReservationId(reservationId: ReservationId): InventoryItem? =
        repository.findAggregateByReservationId(reservationId.value)?.let(mapper::toDomain)

    /**
     * Inserts or optimistically updates a complete inventory aggregate.
     *
     * `saveAndFlush` is important because it forces optimistic-lock and uniqueness violations to
     * occur inside this method, where they can be translated consistently. A uniqueness violation
     * may represent a race between new product inserts or duplicate product/order reservations, so
     * it is treated as a concurrency conflict and the application reloads current state.
     *
     * @param inventoryItem immutable aggregate snapshot to persist.
     * @return persisted domain snapshot containing the updated version.
     * @throws ConcurrentInventoryModificationException when another transaction wins a conflicting
     * update or insert.
     */
    @Transactional
    override fun save(inventoryItem: InventoryItem): InventoryItem = try {
        mapper.toDomain(repository.saveAndFlush(mapper.toEntity(inventoryItem)))
    } catch (exception: OptimisticLockingFailureException) {
        throw ConcurrentInventoryModificationException(exception)
    } catch (exception: DataIntegrityViolationException) {
        // A concurrent insert can race either the product PK or product/order uniqueness constraint.
        throw ConcurrentInventoryModificationException(exception)
    }
}

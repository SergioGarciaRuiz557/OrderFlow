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

@Repository
class JpaInventoryRepositoryAdapter(
    private val repository: SpringDataInventoryRepository,
    private val mapper: InventoryPersistenceMapper,
) : InventoryRepository {

    @Transactional(readOnly = true)
    override fun findByProductId(productId: ProductId): InventoryItem? =
        repository.findAggregateByProductId(productId.value)?.let(mapper::toDomain)

    @Transactional(readOnly = true)
    override fun findByReservationId(reservationId: ReservationId): InventoryItem? =
        repository.findAggregateByReservationId(reservationId.value)?.let(mapper::toDomain)

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

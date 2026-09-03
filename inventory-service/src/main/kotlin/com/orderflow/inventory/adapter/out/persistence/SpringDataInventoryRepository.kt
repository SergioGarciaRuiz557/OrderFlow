package com.orderflow.inventory.adapter.`out`.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Internal Spring Data repository for inventory persistence entities.
 *
 * This interface is an implementation detail of [JpaInventoryRepositoryAdapter] and must not be
 * injected into application or domain code. Custom fetch queries guarantee that mapping receives
 * the complete reservation collection needed to rebuild a valid aggregate.
 */
interface SpringDataInventoryRepository : JpaRepository<InventoryItemJpaEntity, String> {
    /**
     * Fetches one inventory entity and all owned reservations by product primary key.
     *
     * `distinct` removes duplicate parent results produced by the collection join while `left join`
     * still returns inventory that has no reservations.
     *
     * @param productId string primary key stored in `inventory_items`.
     * @return entity graph for the product, or `null` when absent.
     */
    @Query(
        """
        select distinct inventory
        from InventoryItemJpaEntity inventory
        left join fetch inventory.reservations
        where inventory.productId = :productId
        """,
    )
    fun findAggregateByProductId(@Param("productId") productId: String): InventoryItemJpaEntity?

    /**
     * Fetches the complete owning aggregate for a reservation identifier.
     *
     * The subquery first resolves the owning product. The outer left fetch join then loads every
     * reservation for that product rather than only the matching child, which is essential for
     * aggregate invariants and duplicate-order checks.
     *
     * @param reservationId UUID primary key stored in `stock_reservations`.
     * @return complete owning entity graph, or `null` when no reservation matches.
     */
    @Query(
        """
        select distinct inventory
        from InventoryItemJpaEntity inventory
        left join fetch inventory.reservations
        where inventory.productId = (
            select reservation.inventoryItem.productId
            from StockReservationJpaEntity reservation
            where reservation.reservationId = :reservationId
        )
        """,
    )
    fun findAggregateByReservationId(
        @Param("reservationId") reservationId: java.util.UUID,
    ): InventoryItemJpaEntity?
}

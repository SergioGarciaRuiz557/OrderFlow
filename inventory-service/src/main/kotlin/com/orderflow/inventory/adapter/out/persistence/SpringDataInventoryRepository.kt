package com.orderflow.inventory.adapter.`out`.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringDataInventoryRepository : JpaRepository<InventoryItemJpaEntity, String> {
    @Query(
        """
        select distinct inventory
        from InventoryItemJpaEntity inventory
        left join fetch inventory.reservations
        where inventory.productId = :productId
        """,
    )
    fun findAggregateByProductId(@Param("productId") productId: String): InventoryItemJpaEntity?

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

package com.orderflow.inventory.adapter.`out`.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Repositorio interno de Spring Data para las entidades de persistencia del inventario.
 *
 * Esta interfaz es un detalle de implementación de [JpaInventoryRepositoryAdapter] y no debe
 * inyectarse en el código de aplicación ni de dominio. Las consultas de recuperación personalizadas
 * garantizan que el mapeo reciba la colección completa de reservas necesaria para reconstruir un
 * agregado válido.
 */
interface SpringDataInventoryRepository : JpaRepository<InventoryItemJpaEntity, String> {
    /**
     * Recupera una entidad de inventario y todas sus reservas mediante la clave primaria del producto.
     *
     * `distinct` elimina los resultados duplicados de la entidad padre producidos por el join de la
     * colección, mientras que `left join` sigue devolviendo inventarios sin reservas.
     *
     * @param productId clave primaria String almacenada en `inventory_items`.
     * @return grafo de entidades del producto, o `null` cuando no existe.
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
     * Recupera el agregado propietario completo mediante un identificador de reserva.
     *
     * La subconsulta resuelve primero el producto propietario. A continuación, el left fetch join
     * externo carga todas las reservas de ese producto, no solo la entidad hija coincidente, lo que
     * resulta esencial para las invariantes del agregado y las comprobaciones de pedidos duplicados.
     *
     * @param reservationId clave primaria UUID almacenada en `stock_reservations`.
     * @return grafo completo de la entidad propietaria, o `null` cuando no coincide ninguna reserva.
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

    @Query(
        """
        select distinct inventory
        from InventoryItemJpaEntity inventory
        left join fetch inventory.reservations
        where exists (
            select reservation.reservationId from StockReservationJpaEntity reservation
            where reservation.inventoryItem = inventory and reservation.orderId = :orderId
        )
        """,
    )
    fun findAggregatesByOrderId(@Param("orderId") orderId: String): List<InventoryItemJpaEntity>
}

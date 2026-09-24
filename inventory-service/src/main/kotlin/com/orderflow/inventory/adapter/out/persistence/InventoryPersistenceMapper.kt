package com.orderflow.inventory.adapter.`out`.persistence

import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.Quantity
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.ReservationStatus
import com.orderflow.inventory.domain.model.StockReservation
import org.springframework.stereotype.Component

/**
 * Mapeador bidireccional explícito entre las entidades JPA y el agregado de dominio de inventario.
 *
 * El mapeo no se delega intencionadamente en la reflexión ni en un mapeo de beans generado: construir
 * objetos de valor del dominio vuelve a aplicar sus invariantes, mientras que construir entidades JPA
 * gestiona la relación mutable padre/hijo que requiere Hibernate. Esta clase es el único lugar que
 * necesita comprender ambas representaciones.
 */
@Component
class InventoryPersistenceMapper {
    /**
     * Reconstituye un agregado de dominio validado a partir de un grafo de entidades JPA completamente cargado.
     *
     * Cada identificador y cantidad primitivos se convierten en su correspondiente objeto de valor
     * del dominio. Los nombres de estado se convierten al enum del dominio y la versión de persistencia
     * se incorpora al agregado para la siguiente actualización optimista.
     *
     * @param entity fila de inventario con su colección completa de reservas cargada.
     * @return agregado de dominio inmutable que representa la instantánea persistida.
     * @throws IllegalArgumentException si los valores persistidos infringen las invariantes del dominio
     * o contienen un estado de reserva desconocido.
     * @throws IllegalStateException si una entidad supuestamente persistida no tiene versión.
     */
    fun toDomain(entity: InventoryItemJpaEntity): InventoryItem = InventoryItem.reconstitute(
        productId = ProductId(entity.productId),
        availableQuantity = entity.availableQuantity,
        reservations = entity.reservations.map { reservation ->
            StockReservation(
                id = ReservationId(reservation.reservationId),
                orderId = OrderId(reservation.orderId),
                quantity = Quantity(reservation.quantity),
                status = ReservationStatus.valueOf(reservation.status),
                reservedAt = reservation.reservedAt,
                releasedAt = reservation.releasedAt,
            )
        },
        version = requireNotNull(entity.version) { "Persisted inventory must have a version" },
    )

    /**
     * Construye un grafo de entidades JPA a partir de un agregado de dominio inmutable.
     *
     * La versión del agregado se conserva para que Hibernate pueda detectar actualizaciones obsoletas.
     * Cada entidad hija vuelve a enlazarse con la entidad padre recién creada, lo que establece la
     * relación propietaria de clave externa antes de ejecutar `saveAndFlush`.
     *
     * @param inventoryItem instantánea del agregado que se persistirá.
     * @return grafo JPA independiente apto para inserción o fusión optimista.
     */
    fun toEntity(inventoryItem: InventoryItem): InventoryItemJpaEntity {
        // La entidad padre debe existir primero porque cada entidad de reserva referencia esta instancia exacta.
        val entity = InventoryItemJpaEntity(
            productId = inventoryItem.productId.value,
            availableQuantity = inventoryItem.availableQuantity,
            version = inventoryItem.version,
        )
        // Mapear la lista completa garantiza que se persista el estado del agregado, no filas hijas individuales.
        entity.reservations = inventoryItem.reservations.map { reservation ->
            StockReservationJpaEntity(
                reservationId = reservation.id.value,
                orderId = reservation.orderId.value,
                quantity = reservation.quantity.value,
                status = reservation.status.name,
                reservedAt = reservation.reservedAt,
                releasedAt = reservation.releasedAt,
            ).also { it.inventoryItem = entity }
        }.toMutableList()
        return entity
    }
}

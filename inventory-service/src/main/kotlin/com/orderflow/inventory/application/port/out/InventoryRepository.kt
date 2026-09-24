package com.orderflow.inventory.application.port.`out`

import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.OrderId

/**
 * Puerto de persistencia de salida para agregados [InventoryItem] completos.
 *
 * El contrato solo utiliza tipos de dominio, por lo que Spring Data y JPA permanecen fuera del núcleo
 * de la aplicación. Las implementaciones deben cargar la colección completa de reservas porque su
 * contenido participa en las invariantes del agregado y en las decisiones de idempotencia del pedido.
 */
interface InventoryRepository {
    /**
     * Carga el agregado que pertenece a [productId].
     *
     * @return el agregado completo, o `null` cuando no se ha preparado ningún inventario.
     */
    fun findByProductId(productId: ProductId): InventoryItem?

    /**
     * Carga el agregado al que pertenece [reservationId].
     *
     * Esta búsqueda sirve a los comandos de liberación, que identifican una reserva en lugar de un producto.
     *
     * @return el agregado propietario con su historial completo de reservas, o `null` si es desconocido.
     */
    fun findByReservationId(reservationId: ReservationId): InventoryItem?

    /** Carga todos los agregados de producto que contienen una reserva para el pedido. */
    fun findByOrderId(orderId: OrderId): List<InventoryItem>

    /**
     * Persiste un agregado completo utilizando su versión de bloqueo optimista.
     *
     * @param inventoryItem nueva instantánea del agregado que se insertará o actualizará.
     * @return instantánea persistida que contiene la versión asignada por la base de datos.
     * @throws ConcurrentInventoryModificationException si otro escritor modificó un estado incompatible.
     */
    fun save(inventoryItem: InventoryItem): InventoryItem
}

/**
 * Señal independiente de la tecnología que indica que la escritura de un agregado perdió una carrera de concurrencia.
 *
 * Los adaptadores de persistencia traducen a este tipo las excepciones relevantes de unicidad y
 * bloqueo optimista específicas del framework. Así, el servicio de aplicación puede volver a cargar
 * el agregado más reciente y reevaluar las reglas de negocio sin depender de las clases de excepción
 * de Spring Data.
 *
 * @param cause excepción de infraestructura original, conservada para el diagnóstico.
 */
class ConcurrentInventoryModificationException(cause: Throwable? = null) :
    RuntimeException("Inventory was modified concurrently", cause)

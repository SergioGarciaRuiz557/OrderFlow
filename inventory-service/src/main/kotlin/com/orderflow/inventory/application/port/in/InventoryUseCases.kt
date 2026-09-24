package com.orderflow.inventory.application.port.`in`

import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.Quantity
import com.orderflow.inventory.domain.model.ReleaseResult
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.ReservationResult

/**
 * Entrada independiente del transporte necesaria para solicitar una reserva de existencias.
 *
 * Los adaptadores REST o Kafka pueden construir este comando sin introducir sus tipos de transporte
 * en las capas de aplicación o dominio.
 *
 * @property productId producto cuyas existencias disponibles deben asignarse.
 * @property orderId pedido que recibe la asignación y sirve como clave de idempotencia.
 * @property quantity número positivo de unidades solicitadas.
 */
data class ReserveInventoryCommand(
    val productId: ProductId,
    val orderId: OrderId,
    val quantity: Quantity,
)

/**
 * Puerto de entrada de la aplicación para asignar existencias de un producto a un pedido.
 *
 * El puerto expone un resultado explícito del dominio y no presupone si el consumidor es un
 * controlador HTTP, una prueba o el consumidor de Kafka.
 */
fun interface ReserveInventoryUseCase {
    /**
     * Intenta reservar las existencias descritas por [command].
     *
     * @param command identificadores de dominio validados y cantidad positiva solicitada.
     * @return un [ReservationResult] explícito aceptado o rechazado.
     */
    fun reserve(command: ReserveInventoryCommand): ReservationResult
}

/** Puerto de entrada de la aplicación para compensar una reserva aceptada previamente. */
fun interface ReleaseInventoryUseCase {
    /**
     * Libera la reserva identificada si está activa.
     *
     * @param reservationId reserva que se localizará entre los agregados de inventario.
     * @return un resultado explícito que distingue la liberación, su duplicación y un identificador desconocido.
     */
    fun release(reservationId: ReservationId): ReleaseResult
}

/** Puerto de consulta de entrada para recuperar el estado actual de un agregado de inventario. */
fun interface GetInventoryUseCase {
    /**
     * Busca el inventario por producto.
     *
     * @param productId producto cuyas existencias y reservas se solicitan.
     * @return la instantánea del agregado, o `null` cuando no se ha creado un inventario para el producto.
     */
    fun get(productId: ProductId): InventoryItem?
}

/** Puerto administrativo de entrada para preparar o corregir las existencias disponibles. */
fun interface CreateOrUpdateInventoryUseCase {
    /**
     * Crea el inventario si no existe o sustituye la cantidad disponible si ya existe.
     *
     * Se conserva el historial de reservas existente. Esta operación está destinada a la preparación
     * administrativa y de desarrollo, no al procesamiento de reservas impulsado por pedidos.
     *
     * @param productId producto cuyo inventario se está preparando.
     * @param quantity nuevas existencias disponibles; debe ser igual o superior a cero.
     * @return agregado persistido, incluida su versión de bloqueo optimista.
     */
    fun setAvailableQuantity(productId: ProductId, quantity: Int): InventoryItem
}

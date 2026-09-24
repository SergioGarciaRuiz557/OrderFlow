package com.orderflow.inventory.adapter.`in`.rest

import com.orderflow.inventory.application.port.`in`.CreateOrUpdateInventoryUseCase
import com.orderflow.inventory.application.port.`in`.GetInventoryUseCase
import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.ProductId
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Adaptador HTTP de entrada para administrar y consultar el inventario.
 *
 * REST se limita deliberadamente a preparar las existencias disponibles y leer el estado actual.
 * Los flujos de reserva y liberación impulsados por pedidos entran por los puertos de aplicación y
 * pueden conectarse más adelante a Kafka sin cambiar este controlador ni el dominio. En esta frontera,
 * las cadenas de transporte y los DTO JSON se convierten en tipos de dominio como [ProductId].
 *
 * @property createOrUpdateInventory caso de uso empleado por el endpoint administrativo `PUT`.
 * @property getInventory caso de uso de consulta empleado por el endpoint `GET`.
 */
@RestController
@RequestMapping("/api/inventory")
class InventoryController(
    private val createOrUpdateInventory: CreateOrUpdateInventoryUseCase,
    private val getInventory: GetInventoryUseCase,
) {
    /**
     * Crea el inventario o sustituye la cantidad disponible actualmente para reservas.
     *
     * Bean Validation rechaza las cantidades negativas de la solicitud antes de ejecutar el caso de
     * uso. Una operación satisfactoria devuelve la representación actual completa, incluido el
     * historial de reservas.
     *
     * @param productId identificador del producto obtenido de la ruta URL.
     * @param request cuerpo JSON validado que contiene la nueva cantidad disponible.
     * @return representación del agregado de inventario persistido.
     */
    @PutMapping("/{productId}")
    fun setAvailableQuantity(
        @PathVariable productId: String,
        @Valid @RequestBody request: SetInventoryRequest,
    ): InventoryResponse = createOrUpdateInventory
        .setAvailableQuantity(ProductId(productId), request.quantity)
        .toResponse()

    /**
     * Recupera las existencias y el historial de reservas de un producto.
     *
     * @param productId identificador del producto obtenido de la ruta URL.
     * @return representación HTTP del agregado correspondiente.
     * @throws ResponseStatusException con HTTP 404 cuando no se ha preparado el inventario.
     */
    @GetMapping("/{productId}")
    fun get(@PathVariable productId: String): InventoryResponse = getInventory
        .get(ProductId(productId))
        ?.toResponse()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found")
}

/**
 * Cuerpo de la solicitud para la operación administrativa de ajuste de existencias.
 *
 * @property quantity número exacto de unidades que deben estar disponibles tras la actualización;
 * cero es válido y [Min] rechaza los valores negativos.
 */
data class SetInventoryRequest(
    @field:Min(0)
    val quantity: Int,
)

/**
 * Representación externa del inventario devuelta por el adaptador REST.
 *
 * El DTO evita que el agregado de dominio pase a formar parte del contrato JSON público.
 *
 * @property productId identificador externo del producto.
 * @property availableQuantity unidades disponibles para nuevas reservas.
 * @property reservations historial completo y trazable de reservas.
 */
data class InventoryResponse(
    val productId: String,
    val availableQuantity: Int,
    val reservations: List<ReservationResponse>,
)

/**
 * Representación REST de una reserva perteneciente a un elemento de inventario.
 *
 * @property reservationId identificador estable utilizado por futuros comandos de liberación.
 * @property orderId pedido que solicitó la asignación.
 * @property quantity número de unidades asignadas.
 * @property status estado del ciclo de vida del dominio serializado por su nombre.
 * @property reservedAt instante en el que se aceptó la asignación.
 * @property releasedAt instante en el que se repusieron las existencias, o `null` mientras esté activa.
 */
data class ReservationResponse(
    val reservationId: UUID,
    val orderId: String,
    val quantity: Int,
    val status: String,
    val reservedAt: Instant,
    val releasedAt: Instant?,
)

/**
 * Mapea un agregado de dominio al modelo de respuesta HTTP.
 *
 * Mantener el mapeador privado en este adaptador evita que los aspectos de transporte se filtren al
 * dominio y convierte cada campo expuesto externamente en una decisión explícita.
 *
 * @receiver agregado de inventario que se serializará.
 * @return representación REST independiente del agregado y sus reservas.
 */
private fun InventoryItem.toResponse(): InventoryResponse = InventoryResponse(
    productId = productId.value,
    availableQuantity = availableQuantity,
    reservations = reservations.map { reservation ->
        ReservationResponse(
            reservationId = reservation.id.value,
            orderId = reservation.orderId.value,
            quantity = reservation.quantity.value,
            status = reservation.status.name,
            reservedAt = reservation.reservedAt,
            releasedAt = reservation.releasedAt,
        )
    },
)

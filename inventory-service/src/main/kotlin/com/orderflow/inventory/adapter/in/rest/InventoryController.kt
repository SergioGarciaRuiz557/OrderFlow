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
 * Inbound HTTP adapter for inventory administration and inspection.
 *
 * REST is intentionally limited to preparing available stock and reading current state. Order-driven
 * reservation and release flows enter through application ports and can later be connected to Kafka
 * without changing this controller or the domain. Transport strings and JSON DTOs are converted at
 * this boundary into domain types such as [ProductId].
 *
 * @property createOrUpdateInventory use case used by the administrative `PUT` endpoint.
 * @property getInventory query use case used by the `GET` endpoint.
 */
@RestController
@RequestMapping("/api/inventory")
class InventoryController(
    private val createOrUpdateInventory: CreateOrUpdateInventoryUseCase,
    private val getInventory: GetInventoryUseCase,
) {
    /**
     * Creates inventory or replaces the quantity currently available for reservations.
     *
     * Bean Validation rejects negative request quantities before the use case runs. A successful
     * operation returns the complete current representation, including reservation history.
     *
     * @param productId product identifier taken from the URL path.
     * @param request validated JSON body containing the new available quantity.
     * @return representation of the persisted inventory aggregate.
     */
    @PutMapping("/{productId}")
    fun setAvailableQuantity(
        @PathVariable productId: String,
        @Valid @RequestBody request: SetInventoryRequest,
    ): InventoryResponse = createOrUpdateInventory
        .setAvailableQuantity(ProductId(productId), request.quantity)
        .toResponse()

    /**
     * Retrieves stock and reservation history for one product.
     *
     * @param productId product identifier taken from the URL path.
     * @return HTTP representation of the matching aggregate.
     * @throws ResponseStatusException with HTTP 404 when inventory has not been prepared.
     */
    @GetMapping("/{productId}")
    fun get(@PathVariable productId: String): InventoryResponse = getInventory
        .get(ProductId(productId))
        ?.toResponse()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found")
}

/**
 * Request body for the administrative stock-setting operation.
 *
 * @property quantity exact number of units that should be available after the update; zero is valid
 * and negative values are rejected by [Min].
 */
data class SetInventoryRequest(
    @field:Min(0)
    val quantity: Int,
)

/**
 * External inventory representation returned by the REST adapter.
 *
 * The DTO prevents the domain aggregate from becoming part of the public JSON contract.
 *
 * @property productId external product identifier.
 * @property availableQuantity units available for new reservations.
 * @property reservations complete traceable reservation history.
 */
data class InventoryResponse(
    val productId: String,
    val availableQuantity: Int,
    val reservations: List<ReservationResponse>,
)

/**
 * REST representation of one reservation owned by an inventory item.
 *
 * @property reservationId stable identifier used for future release commands.
 * @property orderId order that requested the allocation.
 * @property quantity number of allocated units.
 * @property status domain lifecycle state serialized by name.
 * @property reservedAt instant at which allocation was accepted.
 * @property releasedAt instant at which stock was restored, or `null` while active.
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
 * Maps a domain aggregate to the HTTP response model.
 *
 * Keeping the mapper private to this adapter prevents transport concerns from leaking into the
 * domain and makes every externally exposed field an explicit decision.
 *
 * @receiver inventory aggregate to serialize.
 * @return detached REST representation of the aggregate and its reservations.
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

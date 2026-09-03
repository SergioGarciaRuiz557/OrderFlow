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

@RestController
@RequestMapping("/api/inventory")
class InventoryController(
    private val createOrUpdateInventory: CreateOrUpdateInventoryUseCase,
    private val getInventory: GetInventoryUseCase,
) {
    @PutMapping("/{productId}")
    fun setAvailableQuantity(
        @PathVariable productId: String,
        @Valid @RequestBody request: SetInventoryRequest,
    ): InventoryResponse = createOrUpdateInventory
        .setAvailableQuantity(ProductId(productId), request.quantity)
        .toResponse()

    @GetMapping("/{productId}")
    fun get(@PathVariable productId: String): InventoryResponse = getInventory
        .get(ProductId(productId))
        ?.toResponse()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found")
}

data class SetInventoryRequest(
    @field:Min(0)
    val quantity: Int,
)

data class InventoryResponse(
    val productId: String,
    val availableQuantity: Int,
    val reservations: List<ReservationResponse>,
)

data class ReservationResponse(
    val reservationId: UUID,
    val orderId: String,
    val quantity: Int,
    val status: String,
    val reservedAt: Instant,
    val releasedAt: Instant?,
)

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

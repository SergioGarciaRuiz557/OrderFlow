package com.orderflow.inventory.application.service

import com.orderflow.inventory.application.port.`in`.ReserveInventoryCommand
import com.orderflow.inventory.application.port.`out`.ClockProvider
import com.orderflow.inventory.application.port.`out`.InventoryRepository
import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.Quantity
import com.orderflow.inventory.domain.model.ReleaseResult
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.ReservationRejectionReason
import com.orderflow.inventory.domain.model.ReservationResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class InventoryApplicationServiceTest {
    private val repository = mockk<InventoryRepository>()
    private val clock = mockk<ClockProvider>()
    private val service = InventoryApplicationService(repository, clock)
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `reserve orchestrates aggregate and repository`() {
        val productId = ProductId("product-1")
        every { repository.findByProductId(productId) } returns InventoryItem.create(productId, 10)
        every { clock.now() } returns now
        every { repository.save(any()) } answers { firstArg() }

        val result = service.reserve(ReserveInventoryCommand(productId, OrderId("order-1"), Quantity(3)))

        assertTrue(result is ReservationResult.Reserved)
        result as ReservationResult.Reserved
        assertEquals(7, result.inventoryItem.availableQuantity)
        verify(exactly = 1) { repository.save(match { it.availableQuantity == 7 }) }
    }

    @Test
    fun `missing inventory is an explicit business rejection`() {
        val productId = ProductId("missing")
        every { repository.findByProductId(productId) } returns null

        val result = service.reserve(ReserveInventoryCommand(productId, OrderId("order-1"), Quantity(1)))

        assertEquals(
            ReservationResult.Rejected(ReservationRejectionReason.INVENTORY_ITEM_NOT_FOUND, null),
            result,
        )
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `unknown release remains visible to caller`() {
        val reservationId = ReservationId(UUID.randomUUID())
        every { repository.findByReservationId(reservationId) } returns null

        val result = service.release(reservationId)

        assertEquals(ReleaseResult.ReservationNotFound(reservationId), result)
        verify(exactly = 0) { repository.save(any()) }
    }
}

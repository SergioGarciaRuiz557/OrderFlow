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

/**
 * Unit tests for orchestration performed by [InventoryApplicationService].
 *
 * MockK replaces outbound ports so these tests verify application decisions independently of Spring,
 * JPA, PostgreSQL, and wall-clock time. Domain arithmetic has its own focused test suite.
 */
class InventoryApplicationServiceTest {
    /** Mock persistence boundary used to control loaded state and verify writes. */
    private val repository = mockk<InventoryRepository>()

    /** Mock time boundary that makes lifecycle timestamps deterministic. */
    private val clock = mockk<ClockProvider>()

    /** System under test assembled directly without a Spring application context. */
    private val service = InventoryApplicationService(repository, clock)

    /** Fixed time returned for scenarios that create a lifecycle transition. */
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    /**
     * Verifies the successful orchestration path: load, domain reservation, and aggregate save.
     *
     * The assertion on the saved quantity ensures the service persists the aggregate returned by
     * domain behavior rather than the stale instance initially loaded from the repository.
     */
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

    /**
     * Verifies that absent inventory becomes an explicit business rejection without a write.
     *
     * This protects the contract that business failure is represented by the sealed result hierarchy
     * instead of `null`, an exception, or creation of stock implicitly during an order request.
     */
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

    /**
     * Verifies that an unknown release identifier is reported and never persisted silently.
     *
     * The caller can therefore distinguish a duplicate known release from a malformed or stale
     * command referencing a reservation that never existed.
     */
    @Test
    fun `unknown release remains visible to caller`() {
        val reservationId = ReservationId(UUID.randomUUID())
        every { repository.findByReservationId(reservationId) } returns null

        val result = service.release(reservationId)

        assertEquals(ReleaseResult.ReservationNotFound(reservationId), result)
        verify(exactly = 0) { repository.save(any()) }
    }
}

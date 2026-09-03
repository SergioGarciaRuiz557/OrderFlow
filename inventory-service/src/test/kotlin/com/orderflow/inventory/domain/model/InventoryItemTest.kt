package com.orderflow.inventory.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class InventoryItemTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val productId = ProductId("product-1")

    @Test
    fun `reservation decreases stock`() {
        val result = inventory(10).reserve(reservationId(), OrderId("order-1"), Quantity(4), now)

        assertTrue(result is ReservationResult.Reserved)
        result as ReservationResult.Reserved
        assertEquals(6, result.inventoryItem.availableQuantity)
        assertEquals(4, result.reservation.quantity.value)
    }

    @Test
    fun `should reject reservation when requested quantity exceeds available stock`() {
        val result = inventory(3).reserve(reservationId(), OrderId("order-1"), Quantity(4), now)

        assertEquals(
            ReservationResult.Rejected(ReservationRejectionReason.INSUFFICIENT_STOCK, 3),
            result,
        )
    }

    @Test
    fun `stock never becomes negative`() {
        val result = inventory(0).reserve(reservationId(), OrderId("order-1"), Quantity(1), now)

        assertTrue(result is ReservationResult.Rejected)
        assertEquals(0, inventory(0).availableQuantity)
        assertThrows(IllegalArgumentException::class.java) { InventoryItem.create(productId, -1) }
    }

    @Test
    fun `release restores stock`() {
        val reservationId = reservationId()
        val reserved = inventory(10)
            .reserve(reservationId, OrderId("order-1"), Quantity(4), now) as ReservationResult.Reserved

        val released = reserved.inventoryItem.release(reservationId, now.plusSeconds(60))

        assertTrue(released is ReleaseResult.Released)
        released as ReleaseResult.Released
        assertEquals(10, released.inventoryItem.availableQuantity)
        assertEquals(ReservationStatus.RELEASED, released.reservation.status)
    }

    @Test
    fun `duplicate release does not restore stock twice`() {
        val reservationId = reservationId()
        val reserved = inventory(10)
            .reserve(reservationId, OrderId("order-1"), Quantity(4), now) as ReservationResult.Reserved
        val released = reserved.inventoryItem.release(reservationId, now.plusSeconds(60)) as ReleaseResult.Released

        val duplicate = released.inventoryItem.release(reservationId, now.plusSeconds(120))

        assertTrue(duplicate is ReleaseResult.AlreadyReleased)
        duplicate as ReleaseResult.AlreadyReleased
        assertEquals(10, duplicate.inventoryItem.availableQuantity)
    }

    @Test
    fun `same order and quantity returns existing active reservation idempotently`() {
        val originalId = reservationId()
        val first = inventory(10)
            .reserve(originalId, OrderId("order-1"), Quantity(4), now) as ReservationResult.Reserved

        val duplicate = first.inventoryItem.reserve(
            reservationId(),
            OrderId("order-1"),
            Quantity(4),
            now.plusSeconds(1),
        )

        assertTrue(duplicate is ReservationResult.Reserved)
        duplicate as ReservationResult.Reserved
        assertTrue(duplicate.wasAlreadyReserved)
        assertEquals(originalId, duplicate.reservation.id)
        assertSame(first.inventoryItem, duplicate.inventoryItem)
    }

    @Test
    fun `same order cannot create a different active reservation`() {
        val first = inventory(10)
            .reserve(reservationId(), OrderId("order-1"), Quantity(4), now) as ReservationResult.Reserved

        val duplicate = first.inventoryItem.reserve(
            reservationId(),
            OrderId("order-1"),
            Quantity(3),
            now.plusSeconds(1),
        )

        assertEquals(
            ReservationResult.Rejected(ReservationRejectionReason.ORDER_ALREADY_HAS_RESERVATION, 6),
            duplicate,
        )
    }

    @Test
    fun `unknown reservation cannot be released silently`() {
        val unknownId = reservationId()

        assertEquals(ReleaseResult.ReservationNotFound(unknownId), inventory(10).release(unknownId, now))
    }

    @Test
    fun `invalid Quantity is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Quantity(0) }
        assertThrows(IllegalArgumentException::class.java) { Quantity(-1) }
    }

    private fun inventory(quantity: Int) = InventoryItem.create(productId, quantity)
    private fun reservationId() = ReservationId(UUID.randomUUID())
}

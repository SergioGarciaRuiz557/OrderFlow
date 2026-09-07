package com.orderflow.inventory.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Pure Kotlin unit tests for [InventoryItem] aggregate behavior and invariants.
 *
 * No Spring or persistence infrastructure is involved. Each test focuses on a business rule and
 * treats returned aggregates as immutable snapshots of the corresponding domain transition.
 */
class InventoryItemTest {
    /** Deterministic timestamp used for reservation and release transitions. */
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    /** Product shared by test aggregates where product identity is not the subject under test. */
    private val productId = ProductId("product-1")

    /** Verifies that accepting a reservation subtracts exactly the requested number of units. */
    @Test
    fun `reservation decreases stock`() {
        val result = inventory(10).reserve(reservationId(), OrderId("order-1"), Quantity(4), now)

        assertTrue(result is ReservationResult.Reserved)
        result as ReservationResult.Reserved
        assertEquals(6, result.inventoryItem.availableQuantity)
        assertEquals(4, result.reservation.quantity.value)
    }

    /** Verifies insufficient stock produces a rejection carrying the observed availability. */
    @Test
    fun `should reject reservation when requested quantity exceeds available stock`() {
        val result = inventory(3).reserve(reservationId(), OrderId("order-1"), Quantity(4), now)

        assertEquals(
            ReservationResult.Rejected(ReservationRejectionReason.INSUFFICIENT_STOCK, 3),
            result,
        )
    }

    /**
     * Verifies both reservation evaluation and aggregate construction protect non-negative stock.
     */
    @Test
    fun `stock never becomes negative`() {
        val result = inventory(0).reserve(reservationId(), OrderId("order-1"), Quantity(1), now)

        assertTrue(result is ReservationResult.Rejected)
        assertEquals(0, inventory(0).availableQuantity)
        assertThrows(IllegalArgumentException::class.java) { InventoryItem.create(productId, -1) }
    }

    /** Verifies a first release returns all allocated units and changes reservation state. */
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

    /** Verifies an idempotent repeated release cannot add the same units a second time. */
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

    /**
     * Verifies an exact repeated order request returns the original active reservation idempotently.
     *
     * Returning the same aggregate instance demonstrates that no state transition or write is needed.
     */
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

    /** Verifies an order cannot use the same product reservation key for a different quantity. */
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

    /** Verifies a release for an unknown identifier remains an explicit domain outcome. */
    @Test
    fun `unknown reservation cannot be released silently`() {
        val unknownId = reservationId()

        assertEquals(ReleaseResult.ReservationNotFound(unknownId), inventory(10).release(unknownId, now))
    }

    /** Verifies the [Quantity] value object rejects zero and negative reservation requests. */
    @Test
    fun `invalid Quantity is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Quantity(0) }
        assertThrows(IllegalArgumentException::class.java) { Quantity(-1) }
    }

    /**
     * Creates a fresh aggregate fixture with no reservation history.
     *
     * @param quantity available stock assigned to the fixture.
     * @return new inventory aggregate for [productId].
     */
    private fun inventory(quantity: Int) = InventoryItem.create(productId, quantity)

    /** @return a unique reservation identifier that cannot collide with another test action. */
    private fun reservationId() = ReservationId(UUID.randomUUID())
}

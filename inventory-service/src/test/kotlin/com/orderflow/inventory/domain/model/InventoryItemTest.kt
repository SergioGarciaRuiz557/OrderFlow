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
 * Pruebas unitarias en Kotlin puro del comportamiento y las invariantes del agregado [InventoryItem].
 *
 * No interviene Spring ni infraestructura de persistencia. Cada prueba se centra en una regla de
 * negocio y trata los agregados devueltos como instantáneas inmutables de la transición de dominio
 * correspondiente.
 */
class InventoryItemTest {
    /** Marca de tiempo determinista utilizada en las transiciones de reserva y liberación. */
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    /** Producto compartido por agregados de prueba cuando su identidad no es el objeto de la prueba. */
    private val productId = ProductId("product-1")

    /** Verifica que aceptar una reserva reste exactamente el número de unidades solicitado. */
    @Test
    fun `la reserva reduce las existencias`() {
        val result = inventory(10).reserve(reservationId(), OrderId("order-1"), Quantity(4), now)

        assertTrue(result is ReservationResult.Reserved)
        result as ReservationResult.Reserved
        assertEquals(6, result.inventoryItem.availableQuantity)
        assertEquals(4, result.reservation.quantity.value)
    }

    /** Verifica que unas existencias insuficientes produzcan un rechazo con la disponibilidad observada. */
    @Test
    fun `debe rechazar la reserva cuando la cantidad solicitada supera las existencias disponibles`() {
        val result = inventory(3).reserve(reservationId(), OrderId("order-1"), Quantity(4), now)

        assertEquals(
            ReservationResult.Rejected(ReservationRejectionReason.INSUFFICIENT_STOCK, 3),
            result,
        )
    }

    /**
     * Verifica que tanto la evaluación de la reserva como la construcción del agregado protejan
     * unas existencias no negativas.
     */
    @Test
    fun `las existencias nunca son negativas`() {
        val result = inventory(0).reserve(reservationId(), OrderId("order-1"), Quantity(1), now)

        assertTrue(result is ReservationResult.Rejected)
        assertEquals(0, inventory(0).availableQuantity)
        assertThrows(IllegalArgumentException::class.java) { InventoryItem.create(productId, -1) }
    }

    /** Verifica que una primera liberación devuelva todas las unidades y cambie el estado de la reserva. */
    @Test
    fun `la liberación repone las existencias`() {
        val reservationId = reservationId()
        val reserved = inventory(10)
            .reserve(reservationId, OrderId("order-1"), Quantity(4), now) as ReservationResult.Reserved

        val released = reserved.inventoryItem.release(reservationId, now.plusSeconds(60))

        assertTrue(released is ReleaseResult.Released)
        released as ReleaseResult.Released
        assertEquals(10, released.inventoryItem.availableQuantity)
        assertEquals(ReservationStatus.RELEASED, released.reservation.status)
    }

    /** Verifica que una liberación idempotente repetida no pueda añadir las mismas unidades dos veces. */
    @Test
    fun `una liberación duplicada no repone las existencias dos veces`() {
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
     * Verifica que repetir exactamente una solicitud de pedido devuelva de forma idempotente la
     * reserva activa original.
     *
     * Devolver la misma instancia del agregado demuestra que no se necesita una transición de estado
     * ni una escritura.
     */
    @Test
    fun `el mismo pedido y cantidad devuelve idempotentemente la reserva activa existente`() {
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

    /** Verifica que un pedido no pueda usar la misma clave de reserva para una cantidad diferente. */
    @Test
    fun `el mismo pedido no puede crear una reserva activa diferente`() {
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

    /** Verifica que liberar un identificador desconocido siga siendo un resultado explícito del dominio. */
    @Test
    fun `una reserva desconocida no se puede liberar en silencio`() {
        val unknownId = reservationId()

        assertEquals(ReleaseResult.ReservationNotFound(unknownId), inventory(10).release(unknownId, now))
    }

    /** Verifica que el objeto de valor [Quantity] rechace solicitudes de reserva nulas o negativas. */
    @Test
    fun `se rechaza una Quantity no válida`() {
        assertThrows(IllegalArgumentException::class.java) { Quantity(0) }
        assertThrows(IllegalArgumentException::class.java) { Quantity(-1) }
    }

    /**
     * Crea un fixture nuevo del agregado sin historial de reservas.
     *
     * @param quantity existencias disponibles asignadas al fixture.
     * @return nuevo agregado de inventario para [productId].
     */
    private fun inventory(quantity: Int) = InventoryItem.create(productId, quantity)

    /** @return un identificador de reserva único que no puede colisionar con otra acción de prueba. */
    private fun reservationId() = ReservationId(UUID.randomUUID())
}

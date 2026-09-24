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
 * Pruebas unitarias de la orquestación realizada por [InventoryApplicationService].
 *
 * MockK sustituye los puertos de salida para que estas pruebas verifiquen las decisiones de la
 * aplicación con independencia de Spring, JPA, PostgreSQL y la hora del sistema. La aritmética del
 * dominio cuenta con su propia suite de pruebas específica.
 */
class InventoryApplicationServiceTest {
    /** Límite de persistencia simulado para controlar el estado cargado y verificar las escrituras. */
    private val repository = mockk<InventoryRepository>()

    /** Límite temporal simulado que hace deterministas las marcas de tiempo del ciclo de vida. */
    private val clock = mockk<ClockProvider>()

    /** Sistema bajo prueba construido directamente sin un contexto de aplicación de Spring. */
    private val service = InventoryApplicationService(repository, clock)

    /** Instante fijo devuelto en los escenarios que crean una transición del ciclo de vida. */
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    /**
     * Verifica la ruta correcta de orquestación: carga, reserva de dominio y guardado del agregado.
     *
     * La aserción sobre la cantidad guardada garantiza que el servicio persista el agregado devuelto
     * por el comportamiento del dominio y no la instancia obsoleta cargada inicialmente del repositorio.
     */
    @Test
    fun `la reserva orquesta el agregado y el repositorio`() {
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
     * Verifica que un inventario ausente se convierta en un rechazo de negocio explícito sin escritura.
     *
     * Esto protege el contrato por el que un fallo de negocio se representa mediante la jerarquía
     * sellada de resultados, en lugar de `null`, una excepción o la creación implícita de existencias
     * durante una solicitud de pedido.
     */
    @Test
    fun `un inventario ausente es un rechazo de negocio explícito`() {
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
     * Verifica que se informe de un identificador de liberación desconocido y nunca se persista en silencio.
     *
     * Así, el llamador puede distinguir una liberación conocida duplicada de un comando incorrecto u
     * obsoleto que haga referencia a una reserva que nunca existió.
     */
    @Test
    fun `una liberación desconocida permanece visible para el llamador`() {
        val reservationId = ReservationId(UUID.randomUUID())
        every { repository.findByReservationId(reservationId) } returns null

        val result = service.release(reservationId)

        assertEquals(ReleaseResult.ReservationNotFound(reservationId), result)
        verify(exactly = 0) { repository.save(any()) }
    }
}

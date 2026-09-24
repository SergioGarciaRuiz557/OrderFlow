package com.orderflow.payment.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Pruebas unitarias específicas del comportamiento del agregado Payment y de los invariantes de sus objetos de valor.
 *
 * El conjunto no usa contexto de Spring, persistencia ni simulaciones. Por tanto, los fallos identifican
 * directamente una regla de dominio y las pruebas se ejecutan con rapidez. Los nombres de las pruebas
 * expresan el comportamiento de negocio que protege cada escenario.
 */
class PaymentTest {
    /** Un instante fijo de creación mantiene deterministas las aserciones sobre las transiciones. */
    private val createdAt = Instant.parse("2026-01-01T10:00:00Z")

    /** Demuestra que la única transición satisfactoria produce conjuntamente el estado y la referencia del proveedor. */
    @Test
    fun `autoriza un pago pendiente`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-1"), createdAt.plusSeconds(1))

        assertEquals(PaymentStatus.AUTHORIZED, authorized.status)
        assertEquals(PaymentProviderReference("provider-1"), authorized.providerReference)
    }

    /** Demuestra que un rechazo del proveedor se convierte en un estado terminal de rechazo explícito con su motivo. */
    @Test
    fun `rechaza el pago cuando el proveedor rechaza la autorización`() {
        val rejected = pending().reject(PaymentFailureReason("CARD_DECLINED"), createdAt.plusSeconds(1))

        assertEquals(PaymentStatus.REJECTED, rejected.status)
        assertEquals(PaymentFailureReason("CARD_DECLINED"), rejected.failureReason)
    }

    /** Protege frente a una autorización duplicada y la sustitución de una referencia de proveedor existente. */
    @Test
    fun `no autoriza el mismo pago dos veces`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-1"), createdAt.plusSeconds(1))

        assertThrows(IllegalStateException::class.java) {
            authorized.authorize(PaymentProviderReference("provider-2"), createdAt.plusSeconds(2))
        }
    }

    /** Verifica que un estado terminal autorizado no se pueda cambiar mediante otra operación del ciclo de vida. */
    @Test
    fun `no devuelve un pago autorizado al estado pendiente`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-1"), createdAt.plusSeconds(1))

        assertThrows(IllegalStateException::class.java) {
            authorized.reject(PaymentFailureReason("LATE_REJECTION"), createdAt.plusSeconds(2))
        }
        assertEquals(PaymentStatus.AUTHORIZED, authorized.status)
    }

    /** Garantiza que los valores monetarios negativos no válidos fallen antes de poder crear un agregado. */
    @Test
    fun `rechaza un importe de pago negativo`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.euros(BigDecimal("-0.01"))
        }
    }

    /** Confirma que la referencia de trazabilidad devuelta por un proveedor permanezca en el estado autorizado. */
    @Test
    fun `conserva la referencia del proveedor tras la autorización`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-stable"), createdAt.plusSeconds(1))

        assertNotNull(authorized.providerReference)
        assertEquals("provider-stable", authorized.providerReference?.value)
    }

    /** Construye un fixture pendiente nuevo y válido para que cada prueba parta del mismo estado de dominio. */
    private fun pending(): Payment = Payment.pending(
        id = PaymentId.new(),
        orderId = OrderId("order-1"),
        amount = Money.euros(BigDecimal("12.50")),
        paymentMethodId = PaymentMethodId("pm-test-success"),
        createdAt = createdAt,
    )
}

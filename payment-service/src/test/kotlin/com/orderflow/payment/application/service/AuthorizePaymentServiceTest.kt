package com.orderflow.payment.application.service

import com.orderflow.payment.application.port.`in`.AuthorizePaymentCommand
import com.orderflow.payment.application.port.`in`.PaymentAuthorizationResult
import com.orderflow.payment.application.port.`out`.ClockProvider
import com.orderflow.payment.application.port.`out`.GatewayAuthorizationResult
import com.orderflow.payment.application.port.`out`.PaymentAuthorizationLock
import com.orderflow.payment.application.port.`out`.PaymentGateway
import com.orderflow.payment.application.port.`out`.PaymentGatewayException
import com.orderflow.payment.application.port.`out`.PaymentRepository
import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentFailureReason
import com.orderflow.payment.domain.model.PaymentId
import com.orderflow.payment.domain.model.PaymentMethodId
import com.orderflow.payment.domain.model.PaymentProviderReference
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Pruebas unitarias de las decisiones de coordinación e idempotencia de [AuthorizePaymentService].
 *
 * MockK sustituye los puertos de repositorio, pasarela y reloj para que estas pruebas verifiquen el
 * orden de las interacciones y las ramificaciones sin cargar Spring ni PostgreSQL. Las reglas del ciclo
 * de vida del agregado siguen cubiertas por su propio conjunto de dominio; estos escenarios se centran
 * en cómo coordina la aplicación dichas reglas.
 */
class AuthorizePaymentServiceTest {
    /** Límite simulado de persistencia que se usa para controlar el estado existente e inspeccionar las escrituras. */
    private val repository = mockk<PaymentRepository>()

    /** Límite simulado del proveedor que se usa para seleccionar éxito, rechazo o fallo técnico. */
    private val gateway = mockk<PaymentGateway>()

    /** El reloj simulado proporciona marcas de tiempo deterministas de creación y transición. */
    private val clock = mockk<ClockProvider>()

    /**
     * Doble de prueba síncrono del bloqueo.
     *
     * El bloqueo de PostgreSQL cuenta con cobertura de integración específica; las pruebas unitarias
     * de la aplicación solo necesitan que la función se ejecute una vez sin añadir comportamiento de infraestructura.
     */
    private val lock = object : PaymentAuthorizationLock {
        override fun <T : Any> withLock(orderId: OrderId, operation: () -> T): T = operation()
    }

    /** Sistema sometido a prueba ensamblado directamente mediante sus puertos. */
    private val service = AuthorizePaymentService(repository, gateway, clock, lock)

    /** Instante estable del fixture reutilizado en los distintos escenarios. */
    private val now = Instant.parse("2026-01-01T10:00:00Z")

    /** Comando de autorización válido y canónico; cada prueba solo lo copia cuando la intención debe ser diferente. */
    private val command = AuthorizePaymentCommand(
        OrderId("order-1"),
        Money.euros(BigDecimal("19.99")),
        PaymentMethodId("pm-test-success"),
    )

    /**
     * Verifica que la ruta satisfactoria de un pago nuevo cree el estado pendiente, llame una vez a la
     * pasarela con la clave de idempotencia del pedido, transicione el agregado y conserve ambas instantáneas.
     */
    @Test
    fun `el éxito de la pasarela autoriza y conserva el pago`() {
        every { repository.findByOrderId(command.orderId) } returns null
        every { clock.now() } returnsMany listOf(now, now.plusSeconds(1))
        every { repository.save(any()) } answers { firstArg() }
        every { gateway.authorize(any()) } returns GatewayAuthorizationResult.Authorized(
            PaymentProviderReference("provider-1"),
        )

        val result = service.authorize(command)

        assertTrue(result is PaymentAuthorizationResult.Authorized)
        assertEquals(PaymentProviderReference("provider-1"), result.payment.providerReference)
        verify(exactly = 2) { repository.save(any()) }
        verify(exactly = 1) { gateway.authorize(match { it.idempotencyKey == result.payment.orderId }) }
    }

    /** Verifica que un rechazo conocido se conserve y se devuelva como resultado de rechazo de negocio. */
    @Test
    fun `el rechazo de negocio conserva el estado rechazado`() {
        every { repository.findByOrderId(command.orderId) } returns null
        every { clock.now() } returnsMany listOf(now, now.plusSeconds(1))
        every { repository.save(any()) } answers { firstArg() }
        every { gateway.authorize(any()) } returns GatewayAuthorizationResult.Rejected(
            PaymentFailureReason("CARD_DECLINED"),
        )

        val result = service.authorize(command) as PaymentAuthorizationResult.Rejected

        assertEquals(PaymentFailureReason("CARD_DECLINED"), result.reason)
        verify(exactly = 2) { repository.save(any()) }
    }

    /**
     * Verifica que un tiempo de espera agotado del proveedor se propague mientras solo se escribe la
     * instantánea pendiente y reintentable; no se fabrica un estado rechazado a partir de un fallo técnico.
     */
    @Test
    fun `el fallo técnico de la pasarela se propaga y deja conservado el pago pendiente`() {
        every { repository.findByOrderId(command.orderId) } returns null
        every { clock.now() } returns now
        every { repository.save(any()) } answers { firstArg() }
        every { gateway.authorize(any()) } throws PaymentGatewayException("timeout")

        assertThrows(PaymentGatewayException::class.java) { service.authorize(command) }

        verify(exactly = 1) { repository.save(match { it.status.name == "PENDING" }) }
    }

    /** Protege la ruta satisfactoria idempotente frente a cualquier llamada repetida a la pasarela o escritura en la base de datos. */
    @Test
    fun `un pago ya autorizado no llama a la pasarela ni guarda en el repositorio`() {
        val existing = pending().authorize(PaymentProviderReference("provider-existing"), now.plusSeconds(1))
        every { repository.findByOrderId(command.orderId) } returns existing

        val result = service.authorize(command) as PaymentAuthorizationResult.Authorized

        assertTrue(result.alreadyProcessed)
        verify(exactly = 0) { gateway.authorize(any()) }
        verify(exactly = 0) { repository.save(any()) }
    }

    /** Protege la ruta de rechazo idempotente y conserva el motivo de negocio original. */
    @Test
    fun `una autorización rechazada duplicada devuelve el resultado de negocio original`() {
        val existing = pending().reject(PaymentFailureReason("CARD_DECLINED"), now.plusSeconds(1))
        every { repository.findByOrderId(command.orderId) } returns existing

        val result = service.authorize(command) as PaymentAuthorizationResult.Rejected

        assertTrue(result.alreadyProcessed)
        assertEquals(PaymentFailureReason("CARD_DECLINED"), result.reason)
        verify(exactly = 0) { gateway.authorize(any()) }
        verify(exactly = 0) { repository.save(any()) }
    }

    /** Verifica que reutilizar un pedido con un importe modificado falle antes de cualquier efecto secundario externo. */
    @Test
    fun `una solicitud diferente para un pedido existente falla antes de la pasarela`() {
        every { repository.findByOrderId(command.orderId) } returns pending()
        val changed = command.copy(amount = Money.euros(BigDecimal("20.00")))

        assertThrows(PaymentRequestConflictException::class.java) { service.authorize(changed) }

        verify(exactly = 0) { gateway.authorize(any()) }
    }

    /** Crea un fixture pendiente válido que coincide con [command] para los escenarios de duplicado y conflicto. */
    private fun pending(): Payment = Payment.pending(
        PaymentId.new(),
        command.orderId,
        command.amount,
        command.paymentMethodId,
        now,
    )
}

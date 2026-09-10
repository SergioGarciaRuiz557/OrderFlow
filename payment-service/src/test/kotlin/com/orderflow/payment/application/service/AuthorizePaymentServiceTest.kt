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

class AuthorizePaymentServiceTest {
    private val repository = mockk<PaymentRepository>()
    private val gateway = mockk<PaymentGateway>()
    private val clock = mockk<ClockProvider>()
    private val lock = object : PaymentAuthorizationLock {
        override fun <T : Any> withLock(orderId: OrderId, operation: () -> T): T = operation()
    }
    private val service = AuthorizePaymentService(repository, gateway, clock, lock)
    private val now = Instant.parse("2026-01-01T10:00:00Z")
    private val command = AuthorizePaymentCommand(
        OrderId("order-1"),
        Money.euros(BigDecimal("19.99")),
        PaymentMethodId("pm-test-success"),
    )

    @Test
    fun `gateway success authorizes and persists payment`() {
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

    @Test
    fun `business rejection persists rejected state`() {
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

    @Test
    fun `technical gateway failure propagates and leaves pending payment persisted`() {
        every { repository.findByOrderId(command.orderId) } returns null
        every { clock.now() } returns now
        every { repository.save(any()) } answers { firstArg() }
        every { gateway.authorize(any()) } throws PaymentGatewayException("timeout")

        assertThrows(PaymentGatewayException::class.java) { service.authorize(command) }

        verify(exactly = 1) { repository.save(match { it.status.name == "PENDING" }) }
    }

    @Test
    fun `already authorized payment does not call gateway or repository save`() {
        val existing = pending().authorize(PaymentProviderReference("provider-existing"), now.plusSeconds(1))
        every { repository.findByOrderId(command.orderId) } returns existing

        val result = service.authorize(command) as PaymentAuthorizationResult.Authorized

        assertTrue(result.alreadyProcessed)
        verify(exactly = 0) { gateway.authorize(any()) }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `duplicate rejected authorization returns original business result`() {
        val existing = pending().reject(PaymentFailureReason("CARD_DECLINED"), now.plusSeconds(1))
        every { repository.findByOrderId(command.orderId) } returns existing

        val result = service.authorize(command) as PaymentAuthorizationResult.Rejected

        assertTrue(result.alreadyProcessed)
        assertEquals(PaymentFailureReason("CARD_DECLINED"), result.reason)
        verify(exactly = 0) { gateway.authorize(any()) }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `different request for existing order fails before gateway`() {
        every { repository.findByOrderId(command.orderId) } returns pending()
        val changed = command.copy(amount = Money.euros(BigDecimal("20.00")))

        assertThrows(PaymentRequestConflictException::class.java) { service.authorize(changed) }

        verify(exactly = 0) { gateway.authorize(any()) }
    }

    private fun pending(): Payment = Payment.pending(
        PaymentId.new(),
        command.orderId,
        command.amount,
        command.paymentMethodId,
        now,
    )
}

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
 * Unit tests for orchestration and idempotency decisions in [AuthorizePaymentService].
 *
 * MockK replaces repository, gateway, and clock ports so these tests verify interaction order and
 * branching without loading Spring or PostgreSQL. Aggregate lifecycle rules remain covered by their
 * own domain suite; these scenarios focus on how the application coordinates those rules.
 */
class AuthorizePaymentServiceTest {
    /** Mock persistence boundary used to control existing state and inspect writes. */
    private val repository = mockk<PaymentRepository>()

    /** Mock provider boundary used to select success, rejection, or technical failure. */
    private val gateway = mockk<PaymentGateway>()

    /** Mock clock supplies deterministic creation and transition timestamps. */
    private val clock = mockk<ClockProvider>()

    /**
     * Synchronous lock test double.
     *
     * PostgreSQL locking has dedicated integration coverage; application unit tests only need the
     * callback executed once without adding infrastructure behavior.
     */
    private val lock = object : PaymentAuthorizationLock {
        override fun <T : Any> withLock(orderId: OrderId, operation: () -> T): T = operation()
    }

    /** System under test assembled directly through its ports. */
    private val service = AuthorizePaymentService(repository, gateway, clock, lock)

    /** Stable fixture time reused across scenarios. */
    private val now = Instant.parse("2026-01-01T10:00:00Z")

    /** Canonical valid authorization command; individual tests copy it only when intent must differ. */
    private val command = AuthorizePaymentCommand(
        OrderId("order-1"),
        Money.euros(BigDecimal("19.99")),
        PaymentMethodId("pm-test-success"),
    )

    /**
     * Verifies the new-payment success path creates pending state, calls the gateway once with the
     * order idempotency key, transitions the aggregate, and persists both snapshots.
     */
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

    /** Verifies a known decline is persisted and returned as a business rejection result. */
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

    /**
     * Verifies a provider timeout propagates while only the retryable pending snapshot is written;
     * no rejected state is manufactured from a technical failure.
     */
    @Test
    fun `technical gateway failure propagates and leaves pending payment persisted`() {
        every { repository.findByOrderId(command.orderId) } returns null
        every { clock.now() } returns now
        every { repository.save(any()) } answers { firstArg() }
        every { gateway.authorize(any()) } throws PaymentGatewayException("timeout")

        assertThrows(PaymentGatewayException::class.java) { service.authorize(command) }

        verify(exactly = 1) { repository.save(match { it.status.name == "PENDING" }) }
    }

    /** Protects the idempotent success path from any repeated gateway call or database write. */
    @Test
    fun `already authorized payment does not call gateway or repository save`() {
        val existing = pending().authorize(PaymentProviderReference("provider-existing"), now.plusSeconds(1))
        every { repository.findByOrderId(command.orderId) } returns existing

        val result = service.authorize(command) as PaymentAuthorizationResult.Authorized

        assertTrue(result.alreadyProcessed)
        verify(exactly = 0) { gateway.authorize(any()) }
        verify(exactly = 0) { repository.save(any()) }
    }

    /** Protects the idempotent rejection path and preserves the original business reason. */
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

    /** Verifies reuse of an order with changed amount fails before any external side effect. */
    @Test
    fun `different request for existing order fails before gateway`() {
        every { repository.findByOrderId(command.orderId) } returns pending()
        val changed = command.copy(amount = Money.euros(BigDecimal("20.00")))

        assertThrows(PaymentRequestConflictException::class.java) { service.authorize(changed) }

        verify(exactly = 0) { gateway.authorize(any()) }
    }

    /** Creates a valid pending fixture matching [command] for duplicate and conflict scenarios. */
    private fun pending(): Payment = Payment.pending(
        PaymentId.new(),
        command.orderId,
        command.amount,
        command.paymentMethodId,
        now,
    )
}

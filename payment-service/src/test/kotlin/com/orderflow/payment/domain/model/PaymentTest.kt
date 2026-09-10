package com.orderflow.payment.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Focused unit tests for Payment aggregate behavior and value-object invariants.
 *
 * The suite uses no Spring context, persistence, or mocks. Failures therefore identify a domain rule
 * directly and execute quickly. Test names express the business behavior protected by each scenario.
 */
class PaymentTest {
    /** Fixed creation time keeps transition assertions deterministic. */
    private val createdAt = Instant.parse("2026-01-01T10:00:00Z")

    /** Proves the only successful transition produces status and provider-reference state together. */
    @Test
    fun `should authorize a pending payment`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-1"), createdAt.plusSeconds(1))

        assertEquals(PaymentStatus.AUTHORIZED, authorized.status)
        assertEquals(PaymentProviderReference("provider-1"), authorized.providerReference)
    }

    /** Proves a provider decline becomes explicit terminal rejection state with its reason. */
    @Test
    fun `should reject payment when provider rejects authorization`() {
        val rejected = pending().reject(PaymentFailureReason("CARD_DECLINED"), createdAt.plusSeconds(1))

        assertEquals(PaymentStatus.REJECTED, rejected.status)
        assertEquals(PaymentFailureReason("CARD_DECLINED"), rejected.failureReason)
    }

    /** Protects against duplicate authorization and replacement of an existing provider reference. */
    @Test
    fun `should not authorize the same payment twice`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-1"), createdAt.plusSeconds(1))

        assertThrows(IllegalStateException::class.java) {
            authorized.authorize(PaymentProviderReference("provider-2"), createdAt.plusSeconds(2))
        }
    }

    /** Verifies an authorized terminal state cannot be changed through another lifecycle operation. */
    @Test
    fun `should not move authorized payment back to pending`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-1"), createdAt.plusSeconds(1))

        assertThrows(IllegalStateException::class.java) {
            authorized.reject(PaymentFailureReason("LATE_REJECTION"), createdAt.plusSeconds(2))
        }
        assertEquals(PaymentStatus.AUTHORIZED, authorized.status)
    }

    /** Ensures invalid negative monetary values fail before an aggregate can be created. */
    @Test
    fun `should reject negative payment amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.euros(BigDecimal("-0.01"))
        }
    }

    /** Confirms the traceability reference returned by a provider remains in authorized state. */
    @Test
    fun `should preserve provider reference after authorization`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-stable"), createdAt.plusSeconds(1))

        assertNotNull(authorized.providerReference)
        assertEquals("provider-stable", authorized.providerReference?.value)
    }

    /** Builds a fresh valid pending fixture so every test starts from the same domain state. */
    private fun pending(): Payment = Payment.pending(
        id = PaymentId.new(),
        orderId = OrderId("order-1"),
        amount = Money.euros(BigDecimal("12.50")),
        paymentMethodId = PaymentMethodId("pm-test-success"),
        createdAt = createdAt,
    )
}

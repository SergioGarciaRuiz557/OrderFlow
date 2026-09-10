package com.orderflow.payment.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class PaymentTest {
    private val createdAt = Instant.parse("2026-01-01T10:00:00Z")

    @Test
    fun `should authorize a pending payment`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-1"), createdAt.plusSeconds(1))

        assertEquals(PaymentStatus.AUTHORIZED, authorized.status)
        assertEquals(PaymentProviderReference("provider-1"), authorized.providerReference)
    }

    @Test
    fun `should reject payment when provider rejects authorization`() {
        val rejected = pending().reject(PaymentFailureReason("CARD_DECLINED"), createdAt.plusSeconds(1))

        assertEquals(PaymentStatus.REJECTED, rejected.status)
        assertEquals(PaymentFailureReason("CARD_DECLINED"), rejected.failureReason)
    }

    @Test
    fun `should not authorize the same payment twice`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-1"), createdAt.plusSeconds(1))

        assertThrows(IllegalStateException::class.java) {
            authorized.authorize(PaymentProviderReference("provider-2"), createdAt.plusSeconds(2))
        }
    }

    @Test
    fun `should not move authorized payment back to pending`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-1"), createdAt.plusSeconds(1))

        assertThrows(IllegalStateException::class.java) {
            authorized.reject(PaymentFailureReason("LATE_REJECTION"), createdAt.plusSeconds(2))
        }
        assertEquals(PaymentStatus.AUTHORIZED, authorized.status)
    }

    @Test
    fun `should reject negative payment amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.euros(BigDecimal("-0.01"))
        }
    }

    @Test
    fun `should preserve provider reference after authorization`() {
        val authorized = pending().authorize(PaymentProviderReference("provider-stable"), createdAt.plusSeconds(1))

        assertNotNull(authorized.providerReference)
        assertEquals("provider-stable", authorized.providerReference?.value)
    }

    private fun pending(): Payment = Payment.pending(
        id = PaymentId.new(),
        orderId = OrderId("order-1"),
        amount = Money.euros(BigDecimal("12.50")),
        paymentMethodId = PaymentMethodId("pm-test-success"),
        createdAt = createdAt,
    )
}

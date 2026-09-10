package com.orderflow.payment.adapter.`out`.persistence

import com.orderflow.payment.application.port.`in`.AuthorizePaymentCommand
import com.orderflow.payment.application.port.`in`.AuthorizePaymentUseCase
import com.orderflow.payment.application.port.`in`.GetPaymentUseCase
import com.orderflow.payment.application.port.`in`.PaymentAuthorizationResult
import com.orderflow.payment.application.port.`out`.DuplicatePaymentException
import com.orderflow.payment.application.port.`out`.PaymentGatewayException
import com.orderflow.payment.application.port.`out`.PaymentRepository
import com.orderflow.payment.domain.model.Money
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentId
import com.orderflow.payment.domain.model.PaymentMethodId
import com.orderflow.payment.domain.model.PaymentStatus
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PaymentPersistenceIntegrationTest {
    @Autowired private lateinit var repository: PaymentRepository
    @Autowired private lateinit var authorizePayment: AuthorizePaymentUseCase
    @Autowired private lateinit var getPayment: GetPaymentUseCase
    @Autowired private lateinit var flyway: Flyway

    @Test
    fun `Flyway migration and repository persist payment`() {
        val payment = pending("migration-order")

        val saved = repository.save(payment)
        val loaded = repository.findById(saved.id)

        assertEquals(1, flyway.info().applied().size)
        assertNotNull(saved.version)
        assertEquals(saved, loaded)
    }

    @Test
    fun `application authorization is retrievable without a second charge`() {
        val command = AuthorizePaymentCommand(
            OrderId("authorized-order"),
            Money.euros(BigDecimal("31.25")),
            PaymentMethodId("pm-test-success"),
        )

        val first = authorizePayment.authorize(command) as PaymentAuthorizationResult.Authorized
        val duplicate = authorizePayment.authorize(command) as PaymentAuthorizationResult.Authorized

        assertEquals(PaymentStatus.AUTHORIZED, getPayment.getPayment(first.payment.id)?.status)
        assertEquals(first.payment.providerReference, duplicate.payment.providerReference)
        assertTrue(duplicate.alreadyProcessed)
    }

    @Test
    fun `database uniqueness prevents two payments for one order`() {
        repository.save(pending("unique-order"))

        assertThrows(DuplicatePaymentException::class.java) {
            repository.save(pending("unique-order"))
        }
    }

    @Test
    fun `technical gateway failure keeps pending payment for idempotent retry`() {
        val command = AuthorizePaymentCommand(
            OrderId("technical-error-order"),
            Money.euros(BigDecimal("18.00")),
            PaymentMethodId("pm-test-error"),
        )

        assertThrows(PaymentGatewayException::class.java) { authorizePayment.authorize(command) }

        assertEquals(PaymentStatus.PENDING, repository.findByOrderId(command.orderId)?.status)
    }

    private fun pending(orderId: String): Payment = Payment.pending(
        PaymentId.new(),
        OrderId(orderId),
        Money.euros(BigDecimal("10.00")),
        PaymentMethodId("pm-test-success"),
        Instant.parse("2026-01-01T10:00:00Z"),
    )

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}

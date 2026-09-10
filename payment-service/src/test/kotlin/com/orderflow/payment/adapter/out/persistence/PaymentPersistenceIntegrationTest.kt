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

/**
 * End-to-end persistence and transaction tests against a disposable real PostgreSQL instance.
 *
 * [SpringBootTest] starts the production component graph, including Flyway, Hibernate mappings,
 * repository adapter, advisory-lock adapter, application services, and fake gateway. Testcontainers
 * supplies PostgreSQL rather than an in-memory substitute, so SQL constraints, advisory locks,
 * timestamp types, and optimistic version behavior match production semantics.
 *
 * The suite is skipped when Docker is unavailable so domain and application unit tests remain usable
 * in restricted development environments. CI and completion verification should run it with Docker.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PaymentPersistenceIntegrationTest {
    /** Domain-facing repository implemented by the production JPA adapter. */
    @Autowired
    private lateinit var repository: PaymentRepository

    /** Production authorization input port used to exercise lock, gateway, domain, and persistence. */
    @Autowired
    private lateinit var authorizePayment: AuthorizePaymentUseCase

    /** Production read input port used to verify application-level retrieval. */
    @Autowired
    private lateinit var getPayment: GetPaymentUseCase

    /** Flyway runtime metadata used to prove the versioned production migration was applied. */
    @Autowired
    private lateinit var flyway: Flyway

    /**
     * Proves Flyway creates a schema compatible with Hibernate and that a complete pending aggregate
     * survives an insert/load round trip with a database-assigned optimistic-lock version.
     */
    @Test
    fun `Flyway migration and repository persist payment`() {
        val payment = pending("migration-order")

        val saved = repository.save(payment)
        val loaded = repository.findById(saved.id)

        assertEquals(1, flyway.info().applied().size)
        assertNotNull(saved.version)
        assertEquals(saved, loaded)
    }

    /**
     * Exercises a successful authorization twice and verifies the second command returns the same
     * provider reference as an already-processed result instead of creating another charge.
     */
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

    /**
     * Bypasses the application pre-check intentionally to prove PostgreSQL independently enforces the
     * one-payment-per-order invariant and the adapter translates its integrity exception.
     */
    @Test
    fun `database uniqueness prevents two payments for one order`() {
        repository.save(pending("unique-order"))

        assertThrows(DuplicatePaymentException::class.java) {
            repository.save(pending("unique-order"))
        }
    }

    /**
     * Verifies the subtle transaction contract for infrastructure failure: the technical exception
     * reaches the caller, yet the pending payment commits and is available for a stable-key retry.
     */
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

    /** Creates a deterministic valid pending aggregate for direct repository scenarios. */
    private fun pending(orderId: String): Payment = Payment.pending(
        PaymentId.new(),
        OrderId(orderId),
        Money.euros(BigDecimal("10.00")),
        PaymentMethodId("pm-test-success"),
        Instant.parse("2026-01-01T10:00:00Z"),
    )

    /** Static Testcontainers lifecycle and Spring datasource override hooks. */
    companion object {
        /** PostgreSQL version used to execute the production migration and persistence behavior. */
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        /**
         * Replaces local datasource defaults with the running container's connection properties.
         *
         * [DynamicPropertySource] runs before Spring creates its datasource, avoiding hard-coded ports
         * and credentials because Testcontainers chooses them dynamically.
         *
         * @param registry Spring property registry populated for this integration-test context.
         */
        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}

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
 * Pruebas integrales de persistencia y transacciones contra una instancia real y desechable de PostgreSQL.
 *
 * [SpringBootTest] inicia el grafo de componentes de producción, incluidas las asignaciones de Flyway
 * y Hibernate, el adaptador del repositorio, el adaptador del bloqueo asesor, los servicios de
 * aplicación y la pasarela simulada. Testcontainers proporciona PostgreSQL en lugar de un sustituto
 * en memoria, de modo que las restricciones SQL, los bloqueos asesores, los tipos de marcas de tiempo
 * y el comportamiento de la versión optimista coincidan con la semántica de producción.
 *
 * El conjunto se omite cuando Docker no está disponible para que las pruebas unitarias de dominio y
 * aplicación sigan siendo utilizables en entornos de desarrollo restringidos. La integración continua
 * y la verificación final deben ejecutarlo con Docker.
 */
@SpringBootTest(properties = ["orderflow.kafka.enabled=false"])
@Testcontainers(disabledWithoutDocker = true)
class PaymentPersistenceIntegrationTest {
    /** Repositorio dirigido al dominio que implementa el adaptador JPA de producción. */
    @Autowired
    private lateinit var repository: PaymentRepository

    /** Puerto de entrada de autorización de producción que se usa para ejercitar el bloqueo, la pasarela, el dominio y la persistencia. */
    @Autowired
    private lateinit var authorizePayment: AuthorizePaymentUseCase

    /** Puerto de entrada de lectura de producción que se usa para verificar la recuperación en el nivel de aplicación. */
    @Autowired
    private lateinit var getPayment: GetPaymentUseCase

    /** Metadatos de Flyway en tiempo de ejecución que demuestran que se aplicó la migración versionada de producción. */
    @Autowired
    private lateinit var flyway: Flyway

    /**
     * Demuestra que Flyway crea un esquema compatible con Hibernate y que un agregado pendiente
     * completo sobrevive a un ciclo de inserción y carga con una versión de bloqueo optimista asignada
     * por la base de datos.
     */
    @Test
    fun `la migración de Flyway y el repositorio conservan el pago`() {
        val payment = pending("migration-order")

        val saved = repository.save(payment)
        val loaded = repository.findById(saved.id)

        assertEquals(1, flyway.info().applied().size)
        assertNotNull(saved.version)
        assertEquals(saved, loaded)
    }

    /**
     * Ejercita dos veces una autorización satisfactoria y verifica que el segundo comando devuelva la
     * misma referencia del proveedor como resultado ya procesado, en lugar de crear otro cargo.
     */
    @Test
    fun `la autorización de la aplicación se puede recuperar sin un segundo cargo`() {
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
     * Elude intencionadamente la comprobación previa de la aplicación para demostrar que PostgreSQL
     * aplica de forma independiente el invariante de un pago por pedido y que el adaptador traduce su
     * excepción de integridad.
     */
    @Test
    fun `la unicidad de la base de datos impide dos pagos para un pedido`() {
        repository.save(pending("unique-order"))

        assertThrows(DuplicatePaymentException::class.java) {
            repository.save(pending("unique-order"))
        }
    }

    /**
     * Verifica el sutil contrato transaccional para un fallo de infraestructura: la excepción técnica
     * llega al llamador, pero el pago pendiente se confirma y queda disponible para reintentarlo con
     * una clave estable.
     */
    @Test
    fun `el fallo técnico de la pasarela conserva el pago pendiente para un reintento idempotente`() {
        val command = AuthorizePaymentCommand(
            OrderId("technical-error-order"),
            Money.euros(BigDecimal("18.00")),
            PaymentMethodId("pm-test-error"),
        )

        assertThrows(PaymentGatewayException::class.java) { authorizePayment.authorize(command) }

        assertEquals(PaymentStatus.PENDING, repository.findByOrderId(command.orderId)?.status)
    }

    /** Crea un agregado pendiente válido y determinista para escenarios directos del repositorio. */
    private fun pending(orderId: String): Payment = Payment.pending(
        PaymentId.new(),
        OrderId(orderId),
        Money.euros(BigDecimal("10.00")),
        PaymentMethodId("pm-test-success"),
        Instant.parse("2026-01-01T10:00:00Z"),
    )

    /** Ciclo de vida estático de Testcontainers y enlaces de sustitución de la fuente de datos de Spring. */
    companion object {
        /** Versión de PostgreSQL que se usa para ejecutar la migración de producción y el comportamiento de persistencia. */
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        /**
         * Sustituye los valores locales predeterminados de la fuente de datos por las propiedades de conexión del contenedor en ejecución.
         *
         * [DynamicPropertySource] se ejecuta antes de que Spring cree su fuente de datos, lo que evita
         * puertos y credenciales codificados porque Testcontainers los elige dinámicamente.
         *
         * @param registry registro de propiedades de Spring rellenado para este contexto de pruebas de integración.
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

package com.orderflow.inventory.adapter.`out`.persistence

import com.orderflow.inventory.application.port.`in`.CreateOrUpdateInventoryUseCase
import com.orderflow.inventory.application.port.`in`.ReserveInventoryCommand
import com.orderflow.inventory.application.port.`in`.ReserveInventoryUseCase
import com.orderflow.inventory.application.port.`out`.ConcurrentInventoryModificationException
import com.orderflow.inventory.application.port.`out`.InventoryRepository
import com.orderflow.inventory.domain.model.OrderId
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.Quantity
import com.orderflow.inventory.domain.model.ReservationResult
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Pruebas de persistencia de extremo a extremo contra una base de datos PostgreSQL real y desechable.
 *
 * La suite inicia el contexto completo de Spring, aplica las migraciones de producción de Flyway y
 * ejercita el repositorio mediante puertos de aplicación y dominio, en vez de depender de una base
 * de datos en memoria con una semántica de bloqueo diferente. Las pruebas se omiten cuando Docker no
 * está disponible para que las pruebas unitarias ordinarias puedan ejecutarse, pero los entornos de
 * integración continua con Docker ejecutan todos los escenarios de persistencia.
 */
@SpringBootTest(properties = ["orderflow.kafka.enabled=false"])
@Testcontainers(disabledWithoutDocker = true)
class InventoryPersistenceIntegrationTest {
    /** Repositorio orientado al dominio para verificar ciclos de persistencia y escrituras obsoletas. */
    @Autowired
    private lateinit var repository: InventoryRepository

    /** Puerto de entrada de reservas ejercitado con el servicio de aplicación y adaptador JPA de producción. */
    @Autowired
    private lateinit var reserveInventory: ReserveInventoryUseCase

    /** Puerto de entrada administrativo para preparar existencias aisladas de producto en cada prueba. */
    @Autowired
    private lateinit var createOrUpdateInventory: CreateOrUpdateInventoryUseCase

    /** Metadatos de ejecución de Flyway para demostrar que el contexto aplicó las migraciones de producción. */
    @Autowired
    private lateinit var flyway: Flyway

    /**
     * Verifica que Flyway cree un esquema capaz de insertar y cargar inventario versionado.
     *
     * Un ciclo correcto demuestra que los mapeos de Spring Data concuerdan con los nombres de columna
     * de la migración y que la base de datos asigna una versión de bloqueo optimista a un agregado
     * recién insertado.
     */
    @Test
    fun `la migración de Flyway crea un agregado de inventario utilizable`() {
        val productId = ProductId("migration-product")

        val saved = createOrUpdateInventory.setAvailableQuantity(productId, 12)
        val loaded = repository.findByProductId(productId)

        assertEquals(1, flyway.info().applied().size)
        assertNotNull(saved.version)
        assertEquals(12, loaded?.availableQuantity)
    }

    /**
     * Verifica que las existencias y su entidad de reserva persistan como un grafo de agregado completo.
     *
     * La prueba también ejercita la búsqueda por identificador de reserva y confirma que devuelve el
     * propietario completo, en lugar de una colección de reservas inicializada parcialmente.
     */
    @Test
    fun `el repositorio conserva el estado de la reserva durante un ciclo completo`() {
        val productId = ProductId("round-trip-product")
        createOrUpdateInventory.setAvailableQuantity(productId, 8)

        val result = reserveInventory.reserve(
            ReserveInventoryCommand(productId, OrderId("round-trip-order"), Quantity(3)),
        ) as ReservationResult.Reserved
        val loaded = repository.findByProductId(productId)

        assertEquals(5, loaded?.availableQuantity)
        assertEquals(result.reservation, loaded?.reservations?.single())
        assertEquals(loaded, repository.findByReservationId(result.reservation.id))
    }

    /**
     * Demuestra que dos instantáneas con la misma versión no pueden sobrescribir ambas el inventario.
     *
     * Guardar la primera copia incrementa la versión de la base de datos. La excepción de Hibernate
     * al guardar la copia obsoleta debe traducirse a [ConcurrentInventoryModificationException] y la
     * cantidad ganadora debe permanecer almacenada.
     */
    @Test
    fun `actualizar un agregado obsoleto falla por el bloqueo optimista`() {
        val productId = ProductId("locking-product")
        createOrUpdateInventory.setAvailableQuantity(productId, 5)
        val firstCopy = requireNotNull(repository.findByProductId(productId))
        val staleCopy = requireNotNull(repository.findByProductId(productId))

        repository.save(firstCopy.setAvailableQuantity(4))

        assertThrows(ConcurrentInventoryModificationException::class.java) {
            repository.save(staleCopy.setAvailableQuantity(3))
        }
        assertEquals(4, repository.findByProductId(productId)?.availableQuantity)
    }

    /**
     * Ejercita la ruta real de concurrencia cuando dos pedidos compiten por la última unidad disponible.
     *
     * Los latches sincronizan ambos hilos de trabajo al inicio. Con independencia de qué transacción
     * gane, el resultado esperado es exactamente una reserva, un rechazo de negocio y cero existencias
     * restantes. Esto verifica que reintentar los conflictos optimistas vuelve a evaluar el estado
     * actual del dominio y evita vender por encima de las existencias.
     */
    @Test
    fun `las reservas concurrentes no pueden sobrevender la última unidad`() {
        val productId = ProductId("concurrent-product")
        createOrUpdateInventory.setAvailableQuantity(productId, 1)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            // Cada tarea representa un pedido independiente que entra simultáneamente en el servicio.
            val futures = (1..2).map { orderNumber ->
                executor.submit<ReservationResult> {
                    ready.countDown()
                    start.await(10, TimeUnit.SECONDS)
                    reserveInventory.reserve(
                        ReserveInventoryCommand(
                            productId,
                            OrderId("concurrent-order-$orderNumber"),
                            Quantity(1),
                        ),
                    )
                }
            }
            // Espera a que ambos trabajadores estén listos antes de abrir la barrera de inicio compartida.
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val results = futures.map { it.get(20, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it is ReservationResult.Reserved })
            assertEquals(1, results.count { it is ReservationResult.Rejected })
            assertEquals(0, repository.findByProductId(productId)?.availableQuantity)
        } finally {
            // Libera siempre los recursos del ejecutor, incluso si falla una aserción o un future.
            executor.shutdownNow()
        }
    }

    /** Integración estática de Testcontainers y propiedades dinámicas de Spring. */
    companion object {
        /** Versión de PostgreSQL utilizada para ejecutar el esquema y los bloqueos de producción. */
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        /**
         * Sustituye la configuración local del datasource por los datos de conexión del contenedor.
         *
         * @param registry registro de propiedades de Spring rellenado antes de iniciar el contexto.
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

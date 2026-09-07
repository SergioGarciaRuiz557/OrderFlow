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
 * End-to-end persistence tests against a real disposable PostgreSQL database.
 *
 * The suite starts the complete Spring context, applies production Flyway migrations, and exercises
 * the repository through application/domain ports rather than relying on an in-memory database with
 * different locking semantics. Tests are skipped when Docker is unavailable so ordinary unit tests
 * remain runnable, but CI environments with Docker execute every persistence scenario.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InventoryPersistenceIntegrationTest {
    /** Domain-facing repository used to verify persistence round trips and stale writes. */
    @Autowired
    private lateinit var repository: InventoryRepository

    /** Reservation input port exercised with the production application service and JPA adapter. */
    @Autowired
    private lateinit var reserveInventory: ReserveInventoryUseCase

    /** Administrative input port used to prepare isolated product stock for each test. */
    @Autowired
    private lateinit var createOrUpdateInventory: CreateOrUpdateInventoryUseCase

    /** Flyway runtime metadata used to prove production migrations were applied by the test context. */
    @Autowired
    private lateinit var flyway: Flyway

    /**
     * Verifies that Flyway creates a schema capable of inserting and loading versioned inventory.
     *
     * A successful round trip proves that Spring Data mappings agree with the migration column names
     * and that the database assigns an optimistic-lock version to a newly inserted aggregate.
     */
    @Test
    fun `Flyway migration creates a usable inventory aggregate`() {
        val productId = ProductId("migration-product")

        val saved = createOrUpdateInventory.setAvailableQuantity(productId, 12)
        val loaded = repository.findByProductId(productId)

        assertEquals(1, flyway.info().applied().size)
        assertNotNull(saved.version)
        assertEquals(12, loaded?.availableQuantity)
    }

    /**
     * Verifies that stock and its reservation entity persist as one complete aggregate graph.
     *
     * The test also exercises lookup by reservation identifier and confirms that this lookup returns
     * the whole owner rather than a partially initialized reservation collection.
     */
    @Test
    fun `repository persists reservation state round trip`() {
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
     * Proves that two snapshots with the same version cannot both overwrite inventory state.
     *
     * Saving the first copy increments the database version. Saving the stale copy must be translated
     * from Hibernate's exception into [ConcurrentInventoryModificationException], and the winning
     * quantity must remain stored.
     */
    @Test
    fun `stale aggregate update fails with optimistic locking`() {
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
     * Exercises the real concurrency path when two orders compete for the final available unit.
     *
     * Latches align both worker threads at the start. Regardless of which transaction wins, the
     * expected outcome is exactly one reservation, one business rejection, and zero remaining stock.
     * This verifies that retrying optimistic conflicts re-evaluates current domain state and prevents
     * overselling.
     */
    @Test
    fun `concurrent reservations cannot oversell final unit`() {
        val productId = ProductId("concurrent-product")
        createOrUpdateInventory.setAvailableQuantity(productId, 1)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            // Each task represents an independent order entering the service concurrently.
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
            // Wait until both workers are ready before releasing the shared start gate.
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val results = futures.map { it.get(20, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it is ReservationResult.Reserved })
            assertEquals(1, results.count { it is ReservationResult.Rejected })
            assertEquals(0, repository.findByProductId(productId)?.availableQuantity)
        } finally {
            // Always release executor resources, including when an assertion or future fails.
            executor.shutdownNow()
        }
    }

    /** Static Testcontainers and Spring dynamic-property integration. */
    companion object {
        /** PostgreSQL version used to execute the production schema and locking behavior. */
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        /**
         * Replaces local datasource settings with the container's runtime connection details.
         *
         * @param registry Spring property registry populated before the application context starts.
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

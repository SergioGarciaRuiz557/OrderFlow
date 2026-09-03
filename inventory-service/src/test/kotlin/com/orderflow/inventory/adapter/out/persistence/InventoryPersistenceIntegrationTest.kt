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

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InventoryPersistenceIntegrationTest {
    @Autowired
    private lateinit var repository: InventoryRepository

    @Autowired
    private lateinit var reserveInventory: ReserveInventoryUseCase

    @Autowired
    private lateinit var createOrUpdateInventory: CreateOrUpdateInventoryUseCase

    @Autowired
    private lateinit var flyway: Flyway

    @Test
    fun `Flyway migration creates a usable inventory aggregate`() {
        val productId = ProductId("migration-product")

        val saved = createOrUpdateInventory.setAvailableQuantity(productId, 12)
        val loaded = repository.findByProductId(productId)

        assertEquals(1, flyway.info().applied().size)
        assertNotNull(saved.version)
        assertEquals(12, loaded?.availableQuantity)
    }

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

    @Test
    fun `concurrent reservations cannot oversell final unit`() {
        val productId = ProductId("concurrent-product")
        createOrUpdateInventory.setAvailableQuantity(productId, 1)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
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
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            val results = futures.map { it.get(20, TimeUnit.SECONDS) }

            assertEquals(1, results.count { it is ReservationResult.Reserved })
            assertEquals(1, results.count { it is ReservationResult.Rejected })
            assertEquals(0, repository.findByProductId(productId)?.availableQuantity)
        } finally {
            executor.shutdownNow()
        }
    }

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

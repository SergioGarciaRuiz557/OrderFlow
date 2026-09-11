package com.orderflow.inventory.adapter.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.orderflow.inventory.application.port.`in`.CreateOrUpdateInventoryUseCase
import com.orderflow.inventory.application.port.`in`.GetInventoryUseCase
import com.orderflow.inventory.domain.model.ProductId
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InventoryKafkaIntegrationTest {
    @Autowired lateinit var stock: CreateOrUpdateInventoryUseCase
    @Autowired lateinit var inventory: GetInventoryUseCase
    @Autowired lateinit var json: ObjectMapper

    @Test fun `reserves rejects and releases through Kafka while preserving metadata and key`() {
        stock.setAvailableQuantity(ProductId("PRODUCT-001"), 2)
        consumer("inventory.order.events").use { consumer ->
            val acceptedOrder = UUID.randomUUID()
            val commandId = UUID.randomUUID()
            send(reserve(commandId, acceptedOrder, 2), acceptedOrder)
            val accepted = next(consumer)
            val acceptedJson = json.readTree(accepted.value())
            assertEquals(acceptedOrder.toString(), accepted.key())
            assertEquals("InventoryReservedEvent", acceptedJson["messageType"].asText())
            assertEquals(acceptedOrder.toString(), acceptedJson["correlationId"].asText())
            assertEquals(commandId.toString(), acceptedJson["causationId"].asText())
            assertEquals(0, inventory.get(ProductId("PRODUCT-001"))!!.availableQuantity)

            val rejectedOrder = UUID.randomUUID()
            send(reserve(UUID.randomUUID(), rejectedOrder, 1), rejectedOrder)
            assertEquals("InventoryRejectedEvent", json.readTree(next(consumer).value())["messageType"].asText())

            send(release(UUID.randomUUID(), acceptedOrder), acceptedOrder)
            assertEquals("InventoryReleasedEvent", json.readTree(next(consumer).value())["messageType"].asText())
            assertEquals(2, inventory.get(ProductId("PRODUCT-001"))!!.availableQuantity)
        }
    }

    private fun reserve(messageId: UUID, orderId: UUID, quantity: Int) = envelope(messageId, "ReserveInventoryCommand", orderId,
        "\"items\":[{\"productId\":\"PRODUCT-001\",\"quantity\":$quantity}]")
    private fun release(messageId: UUID, orderId: UUID) = envelope(messageId, "ReleaseInventoryCommand", orderId, null)
    private fun envelope(messageId: UUID, type: String, orderId: UUID, extra: String?): String =
        "{\"messageId\":\"$messageId\",\"messageType\":\"$type\",\"correlationId\":\"$orderId\",\"causationId\":null,\"aggregateId\":\"$orderId\",\"occurredAt\":\"2026-09-10T10:00:00Z\",\"version\":1,\"payload\":{\"orderId\":\"$orderId\"${extra?.let { ",$it" } ?: ""}}}"
    private fun send(value: String, orderId: UUID) {
        KafkaProducer<String, String>(mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to KAFKA.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )).use { it.send(ProducerRecord("order.inventory.commands", orderId.toString(), value)).get() }
    }
    private fun consumer(topic: String) = KafkaConsumer<String, String>(mapOf(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to KAFKA.bootstrapServers,
        ConsumerConfig.GROUP_ID_CONFIG to "inventory-test-${UUID.randomUUID()}",
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
    )).also { it.subscribe(listOf(topic)); it.poll(Duration.ofMillis(100)) }
    private fun next(consumer: KafkaConsumer<String, String>): org.apache.kafka.clients.consumer.ConsumerRecord<String, String> {
        repeat(15) { consumer.poll(Duration.ofSeconds(1)).firstOrNull()?.let { return it } }
        error("Timed out waiting for Kafka record")
    }

    companion object {
        @Container @JvmStatic val POSTGRES = PostgreSQLContainer("postgres:17-alpine")
        @Container @JvmStatic val KAFKA = KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))
        @DynamicPropertySource @JvmStatic fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl)
            registry.add("spring.datasource.username", POSTGRES::getUsername)
            registry.add("spring.datasource.password", POSTGRES::getPassword)
            registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers)
        }
    }
}

package com.orderflow.notification.adapter.`in`.kafka

import com.orderflow.notification.application.port.`in`.SendOrderCancelledNotificationUseCase
import com.orderflow.notification.application.port.`in`.SendOrderConfirmedNotificationUseCase
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class NotificationKafkaIntegrationTest {
    @MockitoBean lateinit var confirmed: SendOrderConfirmedNotificationUseCase
    @MockitoBean lateinit var cancelled: SendOrderCancelledNotificationUseCase

    @Test fun `dispatches both order events through a real broker`() {
        val orderId = UUID.randomUUID()
        send("OrderConfirmedEvent", orderId)
        send("OrderCancelledEvent", orderId)
        await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted {
            assertTrue(mockingDetails(confirmed).invocations.any { it.method.name == "send" })
            assertTrue(mockingDetails(cancelled).invocations.any { it.method.name == "send" })
        }
    }

    private fun send(type: String, orderId: UUID) {
        val customerId = UUID.randomUUID()
        val value = """{"messageId":"${UUID.randomUUID()}","messageType":"$type","correlationId":"$orderId","causationId":null,"aggregateId":"$orderId","occurredAt":"2026-09-10T10:00:00Z","version":1,"payload":{"orderId":"$orderId","customerId":"$customerId"}}"""
        KafkaProducer<String, String>(mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to KAFKA.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )).use { it.send(ProducerRecord("order.events", orderId.toString(), value)).get() }
    }

    companion object {
        @Container @JvmStatic val KAFKA = KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))
        @DynamicPropertySource @JvmStatic fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers)
        }
    }
}

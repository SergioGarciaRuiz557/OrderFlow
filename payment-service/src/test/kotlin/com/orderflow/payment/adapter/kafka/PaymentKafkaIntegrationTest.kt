package com.orderflow.payment.adapter.kafka

import com.fasterxml.jackson.databind.ObjectMapper
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
class PaymentKafkaIntegrationTest {
    @Autowired lateinit var json: ObjectMapper

    @Test fun `publishes authorized and rejected outcomes with the command metadata and key`() {
        consumer().use { consumer ->
            verifyOutcome(consumer, "pm-test-success", "PaymentAuthorizedEvent")
            verifyOutcome(consumer, "pm-test-rejected", "PaymentRejectedEvent")
        }
    }
    private fun verifyOutcome(consumer: KafkaConsumer<String, String>, method: String, expectedType: String) {
        val orderId = UUID.randomUUID(); val commandId = UUID.randomUUID()
        val command = """{"messageId":"$commandId","messageType":"AuthorizePaymentCommand","correlationId":"$orderId","causationId":null,"aggregateId":"$orderId","occurredAt":"2026-09-10T10:00:00Z","version":1,"payload":{"orderId":"$orderId","amount":119.98,"currency":"EUR","paymentMethodId":"$method"}}"""
        KafkaProducer<String, String>(mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to KAFKA.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )).use { it.send(ProducerRecord("order.payment.commands", orderId.toString(), command)).get() }
        val event = next(consumer)
        val body = json.readTree(event.value())
        assertEquals(orderId.toString(), event.key())
        assertEquals(expectedType, body["messageType"].asText())
        assertEquals(orderId.toString(), body["correlationId"].asText())
        assertEquals(commandId.toString(), body["causationId"].asText())
    }
    private fun consumer() = KafkaConsumer<String, String>(mapOf(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to KAFKA.bootstrapServers,
        ConsumerConfig.GROUP_ID_CONFIG to "payment-test-${UUID.randomUUID()}",
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
    )).also { it.subscribe(listOf("payment.order.events")); it.poll(Duration.ofMillis(100)) }
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

package com.orderflow.order.adapter.kafka;

import com.orderflow.order.application.port.in.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderKafkaIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");
    @Container static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
    @MockitoBean HandleInventoryReservedUseCase inventoryReserved;
    @MockitoBean HandleInventoryRejectedUseCase inventoryRejected;
    @MockitoBean HandleInventoryReleasedUseCase inventoryReleased;
    @MockitoBean HandlePaymentAuthorizedUseCase paymentAuthorized;
    @MockitoBean HandlePaymentRejectedUseCase paymentRejected;
    @Autowired CreateOrderUseCase createOrder;
    @Autowired ObjectMapper json;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Test void dispatchesInventoryAndPaymentContractsThroughRealKafka() throws Exception {
        UUID orderId = UUID.randomUUID();
        send("inventory.order.events", envelope("InventoryReservedEvent", orderId, "{}"), orderId);
        send("inventory.order.events", envelope("InventoryRejectedEvent", orderId, "\"reason\":\"INSUFFICIENT_STOCK\""), orderId);
        send("inventory.order.events", envelope("InventoryReleasedEvent", orderId, "{}"), orderId);
        send("payment.order.events", envelope("PaymentAuthorizedEvent", orderId, "{}"), orderId);
        send("payment.order.events", envelope("PaymentRejectedEvent", orderId, "\"reason\":\"PAYMENT_METHOD_REJECTED\""), orderId);
        verify(inventoryReserved, timeout(10_000)).handle(orderId);
        verify(inventoryRejected, timeout(10_000)).handle(orderId, "INSUFFICIENT_STOCK");
        verify(inventoryReleased, timeout(10_000)).handle(orderId);
        verify(paymentAuthorized, timeout(10_000)).handle(orderId);
        verify(paymentRejected, timeout(10_000)).handle(orderId, "PAYMENT_METHOD_REJECTED");
    }

    @Test void publishesOrderCommandsWithOrderKeyAndRootMetadata() throws Exception {
        try (var consumer = consumer("order.inventory.commands")) {
            var created = createOrder.create(new CreateOrderUseCase.CreateOrderCommand(
                    UUID.randomUUID(), List.of(new CreateOrderUseCase.CreateOrderItem(
                    "PRODUCT-001", 2, new BigDecimal("59.99"))), "pm-test-success"));
            var record = next(consumer);
            var body = json.readTree(record.value());
            assertThat(record.key()).isEqualTo(created.orderId().toString());
            assertThat(body.path("messageType").asText()).isEqualTo("ReserveInventoryCommand");
            assertThat(body.path("correlationId").asText()).isEqualTo(created.orderId().toString());
            assertThat(body.path("aggregateId").asText()).isEqualTo(created.orderId().toString());
            assertThat(body.path("causationId").isNull()).isTrue();
        }
    }

    private static String envelope(String type, UUID orderId, String extra) {
        String fields = extra.equals("{}") ? "" : "," + extra;
        return "{\"messageId\":\"" + UUID.randomUUID() + "\",\"messageType\":\"" + type
                + "\",\"correlationId\":\"" + orderId + "\",\"causationId\":null,\"aggregateId\":\"" + orderId
                + "\",\"occurredAt\":\"2026-09-10T10:00:00Z\",\"version\":1,\"payload\":{\"orderId\":\"" + orderId + "\"" + fields + "}}";
    }
    private static void send(String topic, String value, UUID orderId) throws Exception {
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            producer.send(new ProducerRecord<>(topic, orderId.toString(), value)).get();
        }
    }
    private static KafkaConsumer<String, String> consumer(String topic) {
        var consumer = new KafkaConsumer<String, String>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "order-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumer.subscribe(List.of(topic));
        consumer.poll(Duration.ofMillis(100));
        return consumer;
    }
    private static org.apache.kafka.clients.consumer.ConsumerRecord<String, String> next(KafkaConsumer<String, String> consumer) {
        for (int attempt = 0; attempt < 15; attempt++) {
            var records = consumer.poll(Duration.ofSeconds(1));
            if (!records.isEmpty()) return records.iterator().next();
        }
        throw new AssertionError("Timed out waiting for Kafka record");
    }
}

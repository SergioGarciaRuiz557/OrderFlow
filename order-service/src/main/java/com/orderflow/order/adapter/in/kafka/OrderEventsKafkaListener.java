package com.orderflow.order.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.order.adapter.kafka.KafkaMessageContext;
import com.orderflow.order.application.port.in.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "orderflow.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventsKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(OrderEventsKafkaListener.class);
    private final ObjectMapper json;
    private final HandleInventoryReservedUseCase inventoryReserved;
    private final HandleInventoryRejectedUseCase inventoryRejected;
    private final HandleInventoryReleasedUseCase inventoryReleased;
    private final HandlePaymentAuthorizedUseCase paymentAuthorized;
    private final HandlePaymentRejectedUseCase paymentRejected;

    public OrderEventsKafkaListener(ObjectMapper json, HandleInventoryReservedUseCase inventoryReserved,
                                    HandleInventoryRejectedUseCase inventoryRejected,
                                    HandleInventoryReleasedUseCase inventoryReleased,
                                    HandlePaymentAuthorizedUseCase paymentAuthorized,
                                    HandlePaymentRejectedUseCase paymentRejected) {
        this.json = json; this.inventoryReserved = inventoryReserved; this.inventoryRejected = inventoryRejected;
        this.inventoryReleased = inventoryReleased; this.paymentAuthorized = paymentAuthorized; this.paymentRejected = paymentRejected;
    }

    @KafkaListener(topics = "${orderflow.kafka.topics.inventory-events}", groupId = "${orderflow.kafka.groups.inventory-events}")
    public void onInventoryEvent(String value) { dispatch(value, true); }

    @KafkaListener(topics = "${orderflow.kafka.topics.payment-events}", groupId = "${orderflow.kafka.groups.payment-events}")
    public void onPaymentEvent(String value) { dispatch(value, false); }

    private void dispatch(String value, boolean inventoryTopic) {
        JsonNode root;
        try { root = json.readTree(value); } catch (JsonProcessingException e) { throw new IllegalArgumentException("Malformed Kafka JSON", e); }
        UUID messageId = uuid(root, "messageId");
        UUID correlationId = uuid(root, "correlationId");
        UUID aggregateId = uuid(root, "aggregateId");
        String type = text(root, "messageType");
        if (!root.has("causationId")) throw new IllegalArgumentException("causationId field is required");
        if (!root.path("causationId").isNull()) uuid(root, "causationId");
        Instant.parse(text(root, "occurredAt"));
        if (root.path("version").asInt() != 1 || !root.path("payload").isObject()) throw new IllegalArgumentException("Unsupported or invalid envelope");
        JsonNode payload = root.path("payload");
        UUID orderId = uuid(payload, "orderId");
        if (!orderId.equals(aggregateId)) throw new IllegalArgumentException("aggregateId must equal payload.orderId");
        if (!orderId.equals(correlationId)) throw new IllegalArgumentException("correlationId must equal payload.orderId");
        log.info("Kafka message received: messageType={}, messageId={}, correlationId={}, orderId={}", type, messageId, correlationId, orderId);
        KafkaMessageContext.run(new KafkaMessageContext.Metadata(messageId, correlationId), () -> {
            if (inventoryTopic) {
                switch (type) {
                    case "InventoryReservedEvent" -> inventoryReserved.handle(orderId);
                    case "InventoryRejectedEvent" -> inventoryRejected.handle(orderId, text(payload, "reason"));
                    case "InventoryReleasedEvent" -> inventoryReleased.handle(orderId);
                    default -> throw new IllegalArgumentException("Unsupported inventory messageType: " + type);
                }
            } else {
                switch (type) {
                    case "PaymentAuthorizedEvent" -> paymentAuthorized.handle(orderId);
                    case "PaymentRejectedEvent" -> paymentRejected.handle(orderId, text(payload, "reason"));
                    default -> throw new IllegalArgumentException("Unsupported payment messageType: " + type);
                }
            }
        });
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    private static UUID uuid(JsonNode node, String field) {
        try { return UUID.fromString(text(node, field)); } catch (IllegalArgumentException e) { throw new IllegalArgumentException(field + " must be a UUID", e); }
    }
}

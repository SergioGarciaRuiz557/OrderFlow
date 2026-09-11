package com.orderflow.order.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.order.adapter.kafka.KafkaEnvelope;
import com.orderflow.order.adapter.kafka.KafkaMessageContext;
import com.orderflow.order.adapter.kafka.OrderKafkaProperties;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.event.*;
import com.orderflow.order.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "orderflow.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderKafkaMessagePublisher implements IntegrationMessagePublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderKafkaMessagePublisher.class);
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final OrderKafkaProperties properties;
    private final OrderRepository orders;

    public OrderKafkaMessagePublisher(KafkaTemplate<String, String> kafka, ObjectMapper json,
                                      OrderKafkaProperties properties, OrderRepository orders) {
        this.kafka = kafka; this.json = json; this.properties = properties; this.orders = orders;
    }

    @Override public void publish(List<OrderDomainEvent> events) {
        for (OrderDomainEvent event : events) {
            if (event instanceof OrderCreated || event instanceof InventoryReserved
                    || event instanceof InventoryRejected || event instanceof PaymentAuthorized
                    || event instanceof PaymentRejected) continue;
            Order order = orders.findById(event.orderId()).orElseThrow();
            String type; String topic; Object payload;
            if (event instanceof InventoryReservationRequested) {
                type = "ReserveInventoryCommand"; topic = properties.topics().inventoryCommands();
                payload = new ReserveInventoryPayload(order.id().value(), order.lines().stream()
                        .map(line -> new Item(line.productId().value(), line.quantity().value())).toList());
            } else if (event instanceof InventoryReleaseRequested) {
                type = "ReleaseInventoryCommand"; topic = properties.topics().inventoryCommands();
                payload = new ReleaseInventoryPayload(order.id().value());
            } else if (event instanceof PaymentAuthorizationRequested) {
                type = "AuthorizePaymentCommand"; topic = properties.topics().paymentCommands();
                payload = new AuthorizePaymentPayload(order.id().value(), order.total().amount(),
                        order.total().currency().getCurrencyCode(), order.paymentMethodId().value());
            } else if (event instanceof OrderConfirmed) {
                type = "OrderConfirmedEvent"; topic = properties.topics().orderEvents();
                payload = new OrderConfirmedPayload(order.id().value(), order.customerId().value(),
                        order.total().amount(), order.total().currency().getCurrencyCode());
            } else if (event instanceof OrderCancelled cancelled) {
                type = "OrderCancelledEvent"; topic = properties.topics().orderEvents();
                payload = new OrderCancelledPayload(order.id().value(), order.customerId().value(), cancelled.reason());
            } else continue;
            publish(topic, type, event.orderId().value(), event.occurredAt(), payload);
        }
    }

    private void publish(String topic, String type, UUID orderId, Instant occurredAt, Object payload) {
        var incoming = KafkaMessageContext.current();
        UUID correlationId = incoming == null ? orderId : incoming.correlationId();
        UUID causationId = incoming == null ? null : incoming.messageId();
        var envelope = new KafkaEnvelope<>(UUID.randomUUID(), type, correlationId, causationId,
                orderId, occurredAt, 1, payload);
        try {
            kafka.send(topic, orderId.toString(), json.writeValueAsString(envelope)).whenComplete((result, error) -> {
                if (error != null) log.error("Kafka send failed: messageType={}, messageId={}, correlationId={}, orderId={}",
                        type, envelope.messageId(), correlationId, orderId, error);
                else log.info("Kafka message sent: messageType={}, messageId={}, correlationId={}, orderId={}",
                        type, envelope.messageId(), correlationId, orderId);
            });
        } catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize " + type, exception); }
    }

    public record Item(String productId, int quantity) { }
    public record ReserveInventoryPayload(UUID orderId, List<Item> items) { }
    public record ReleaseInventoryPayload(UUID orderId) { }
    public record AuthorizePaymentPayload(UUID orderId, BigDecimal amount, String currency, String paymentMethodId) { }
    public record OrderConfirmedPayload(UUID orderId, UUID customerId, BigDecimal total, String currency) { }
    public record OrderCancelledPayload(UUID orderId, UUID customerId, String reason) { }
}

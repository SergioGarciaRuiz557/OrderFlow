package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

public record PaymentRejected(OrderId orderId, String reason, Instant occurredAt) implements OrderDomainEvent {
}

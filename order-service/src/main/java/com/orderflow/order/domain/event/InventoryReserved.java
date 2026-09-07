package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

public record InventoryReserved(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Fact that Inventory could not reserve the requested products.
 *
 * @param orderId rejected aggregate
 * @param reason normalized business explanation
 * @param occurredAt callback processing time
 */
public record InventoryRejected(OrderId orderId, String reason, Instant occurredAt) implements OrderDomainEvent {
}

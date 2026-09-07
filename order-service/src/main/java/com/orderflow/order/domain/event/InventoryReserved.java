package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Fact that Inventory accepted the order's reservation.
 *
 * @param orderId aggregate whose inventory is reserved
 * @param occurredAt callback processing time
 */
public record InventoryReserved(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

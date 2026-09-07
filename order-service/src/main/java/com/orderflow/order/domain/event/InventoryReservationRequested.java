package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Business request to reserve every product and quantity belonging to an order.
 *
 * @param orderId aggregate requiring reservation
 * @param occurredAt request time
 */
public record InventoryReservationRequested(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

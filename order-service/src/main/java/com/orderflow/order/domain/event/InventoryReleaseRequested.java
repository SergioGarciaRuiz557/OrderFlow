package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Compensation request to release inventory that is no longer needed by an order.
 *
 * @param orderId aggregate whose reservation must be released
 * @param occurredAt compensation request time
 */
public record InventoryReleaseRequested(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Fact recorded after a valid Order aggregate has been created and priced.
 *
 * @param orderId created aggregate
 * @param occurredAt creation time
 */
public record OrderCreated(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Terminal successful fact produced after payment authorization.
 *
 * @param orderId confirmed aggregate
 * @param occurredAt confirmation time
 */
public record OrderConfirmed(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

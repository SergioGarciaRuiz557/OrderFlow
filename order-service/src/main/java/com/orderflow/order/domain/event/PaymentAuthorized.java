package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Fact that Payment authorized the requested amount.
 *
 * @param orderId authorized aggregate
 * @param occurredAt callback processing time
 */
public record PaymentAuthorized(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

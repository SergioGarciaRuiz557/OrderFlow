package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Terminal cancellation fact produced after rejection or completed compensation.
 *
 * @param orderId cancelled aggregate
 * @param reason normalized cancellation reason
 * @param occurredAt cancellation time
 */
public record OrderCancelled(OrderId orderId, String reason, Instant occurredAt) implements OrderDomainEvent {
}

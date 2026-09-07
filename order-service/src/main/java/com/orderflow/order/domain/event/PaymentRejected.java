package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Fact that Payment rejected authorization and compensation must begin.
 *
 * @param orderId rejected aggregate
 * @param reason normalized business explanation
 * @param occurredAt callback processing time
 */
public record PaymentRejected(OrderId orderId, String reason, Instant occurredAt) implements OrderDomainEvent {
}

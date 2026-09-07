package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Business request to authorize the order total using its payment-method reference.
 *
 * @param orderId aggregate requiring payment authorization
 * @param occurredAt request time
 */
public record PaymentAuthorizationRequested(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

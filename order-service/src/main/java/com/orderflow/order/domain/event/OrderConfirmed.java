package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Hecho terminal satisfactorio producido después de autorizar el pago.
 *
 * @param orderId agregado confirmado
 * @param occurredAt instante de confirmación
 */
public record OrderConfirmed(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

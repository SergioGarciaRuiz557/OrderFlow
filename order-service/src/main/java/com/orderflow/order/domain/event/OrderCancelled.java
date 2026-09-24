package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Hecho terminal de cancelación producido después de un rechazo o de completar la compensación.
 *
 * @param orderId agregado cancelado
 * @param reason motivo de cancelación normalizado
 * @param occurredAt instante de cancelación
 */
public record OrderCancelled(OrderId orderId, String reason, Instant occurredAt) implements OrderDomainEvent {
}

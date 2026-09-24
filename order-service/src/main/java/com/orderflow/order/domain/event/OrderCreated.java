package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Hecho registrado después de crear y valorar un agregado Order válido.
 *
 * @param orderId agregado creado
 * @param occurredAt instante de creación
 */
public record OrderCreated(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Solicitud de compensación para liberar el inventario que un pedido ya no necesita.
 *
 * @param orderId agregado cuya reserva debe liberarse
 * @param occurredAt instante de la solicitud de compensación
 */
public record InventoryReleaseRequested(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

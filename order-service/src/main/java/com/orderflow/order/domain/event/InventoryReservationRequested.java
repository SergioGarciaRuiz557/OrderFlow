package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Solicitud de negocio para reservar cada producto y cantidad pertenecientes a un pedido.
 *
 * @param orderId agregado que requiere la reserva
 * @param occurredAt instante de la solicitud
 */
public record InventoryReservationRequested(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

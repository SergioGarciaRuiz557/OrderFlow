package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Hecho que indica que Inventory ha aceptado la reserva del pedido.
 *
 * @param orderId agregado cuyo inventario está reservado
 * @param occurredAt instante de procesamiento de la notificación
 */
public record InventoryReserved(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

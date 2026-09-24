package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Hecho que indica que Inventory no ha podido reservar los productos solicitados.
 *
 * @param orderId agregado rechazado
 * @param reason explicación de negocio normalizada
 * @param occurredAt instante de procesamiento de la notificación
 */
public record InventoryRejected(OrderId orderId, String reason, Instant occurredAt) implements OrderDomainEvent {
}

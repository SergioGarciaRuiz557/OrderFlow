package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Hecho que indica que Payment ha rechazado la autorización y debe comenzar la compensación.
 *
 * @param orderId agregado rechazado
 * @param reason explicación de negocio normalizada
 * @param occurredAt instante de procesamiento de la notificación
 */
public record PaymentRejected(OrderId orderId, String reason, Instant occurredAt) implements OrderDomainEvent {
}

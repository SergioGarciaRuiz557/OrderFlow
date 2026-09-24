package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Hecho que indica que Payment ha autorizado el importe solicitado.
 *
 * @param orderId agregado autorizado
 * @param occurredAt instante de procesamiento de la notificación
 */
public record PaymentAuthorized(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

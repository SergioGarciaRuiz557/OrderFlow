package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Solicitud de negocio para autorizar el total del pedido mediante la referencia de su método de pago.
 *
 * @param orderId agregado que requiere la autorización del pago
 * @param occurredAt instante de la solicitud
 */
public record PaymentAuthorizationRequested(OrderId orderId, Instant occurredAt) implements OrderDomainEvent {
}

package com.orderflow.order.application.port.out;

import com.orderflow.order.domain.event.OrderDomainEvent;

import java.util.List;

/**
 * Límite de salida para entregar hechos de negocio más allá de la transacción local.
 *
 * <p>El adaptador de Kafka traduce los eventos de dominio admitidos a mensajes de integración versionados, mientras
 * esta interfaz de aplicación permanece independiente del transporte.</p>
 */
public interface IntegrationMessagePublisher {
    /**
     * Publica los eventos producidos por una operación del agregado en su orden original.
     *
     * @param events lote de eventos inmutable; puede estar vacío tras una notificación idempotente
     */
    void publish(List<OrderDomainEvent> events);
}

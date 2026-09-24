package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

/**
 * Contrato común de los hechos emitidos por el agregado Order.
 *
 * <p>El contrato contiene intencionadamente solo los datos de dominio compartidos por todos los eventos. Los metadatos
 * de entrega, las claves de Kafka, las cabeceras, las versiones del esquema y la serialización pertenecen al adaptador de salida.</p>
 */
public interface OrderDomainEvent {
    /**
     * Identifica el agregado que produjo este hecho.
     *
     * @return agregado que produjo el evento
     */
    OrderId orderId();

    /**
     * Identifica cuándo ocurrió el hecho según el reloj de la aplicación.
     *
     * @return instante de negocio en el que ocurrió la transición
     */
    Instant occurredAt();
}

package com.orderflow.order.application.exception;

import com.orderflow.order.domain.model.OrderId;

/**
 * Indica que un caso de uso de la aplicación no ha podido cargar el agregado solicitado.
 *
 * <p>Mantenerla separada de las excepciones del repositorio o de JPA permite que cada adaptador de entrada
 * represente la ausencia según su propio protocolo.</p>
 */
public class OrderNotFoundException extends RuntimeException {
    /**
     * Crea una excepción cuyo mensaje incluye la identidad ausente.
     *
     * @param orderId identificador solicitado por el consumidor
     */
    public OrderNotFoundException(OrderId orderId) {
        super("Order not found: " + orderId);
    }
}

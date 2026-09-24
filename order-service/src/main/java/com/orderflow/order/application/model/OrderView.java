package com.orderflow.order.application.model;

import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado independiente de la tecnología que devuelven los casos de uso de la aplicación Order.
 *
 * <p>El adaptador REST mapea esta vista a JSON, pero el propio tipo no tiene anotaciones web y puede
 * reutilizarse en otros adaptadores de entrada.</p>
 *
 * @param orderId identidad del agregado
 * @param status estado actual del ciclo de vida
 * @param total total decimal calculado
 * @param currency código ISO de la divisa
 * @param createdAt instante de creación del agregado
 * @param updatedAt instante de la última transición
 */
public record OrderView(UUID orderId, OrderStatus status, BigDecimal total, String currency,
                        Instant createdAt, Instant updatedAt) {
    /**
     * Crea un resultado de la aplicación a partir de un agregado del dominio sin exponer objetos de valor del dominio.
     *
     * @param order agregado de origen
     * @return vista aplanada de la aplicación
     */
    public static OrderView from(Order order) {
        return new OrderView(order.id().value(), order.status(), order.total().amount(),
                order.total().currency().getCurrencyCode(), order.createdAt(), order.updatedAt());
    }
}

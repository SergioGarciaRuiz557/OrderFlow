package com.orderflow.order.adapter.in.rest;

import com.orderflow.order.application.model.OrderView;
import com.orderflow.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representación JSON estable que devuelven los endpoints REST de Order.
 *
 * @param orderId UUID del agregado
 * @param status estado actual del ciclo de vida
 * @param total total decimal calculado por el dominio
 * @param currency código ISO de la divisa
 * @param createdAt marca temporal de creación
 * @param updatedAt marca temporal de la última transición
 */
public record OrderResponse(UUID orderId, OrderStatus status, BigDecimal total, String currency,
                            Instant createdAt, Instant updatedAt) {
    /**
     * Mapea un resultado de la aplicación a su representación HTTP.
     *
     * @param view resultado de la aplicación
     * @return DTO de respuesta sin objetos del dominio expuestos
     */
    static OrderResponse from(OrderView view) {
        return new OrderResponse(view.orderId(), view.status(), view.total(), view.currency(),
                view.createdAt(), view.updatedAt());
    }
}

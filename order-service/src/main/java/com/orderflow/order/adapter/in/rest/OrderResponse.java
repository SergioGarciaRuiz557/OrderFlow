package com.orderflow.order.adapter.in.rest;

import com.orderflow.order.application.model.OrderView;
import com.orderflow.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Stable JSON representation returned by Order REST endpoints.
 *
 * @param orderId aggregate UUID
 * @param status current lifecycle state
 * @param total domain-calculated decimal total
 * @param currency ISO currency code
 * @param createdAt creation timestamp
 * @param updatedAt latest transition timestamp
 */
public record OrderResponse(UUID orderId, OrderStatus status, BigDecimal total, String currency,
                            Instant createdAt, Instant updatedAt) {
    /**
     * Maps an application result to its HTTP representation.
     *
     * @param view application result
     * @return response DTO with no domain objects exposed
     */
    static OrderResponse from(OrderView view) {
        return new OrderResponse(view.orderId(), view.status(), view.total(), view.currency(),
                view.createdAt(), view.updatedAt());
    }
}

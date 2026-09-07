package com.orderflow.order.adapter.in.rest;

import com.orderflow.order.application.model.OrderView;
import com.orderflow.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID orderId, OrderStatus status, BigDecimal total, String currency,
                            Instant createdAt, Instant updatedAt) {
    static OrderResponse from(OrderView view) {
        return new OrderResponse(view.orderId(), view.status(), view.total(), view.currency(),
                view.createdAt(), view.updatedAt());
    }
}

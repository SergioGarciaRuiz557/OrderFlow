package com.orderflow.order.application.model;

import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderView(UUID orderId, OrderStatus status, BigDecimal total, String currency,
                        Instant createdAt, Instant updatedAt) {
    public static OrderView from(Order order) {
        return new OrderView(order.id().value(), order.status(), order.total().amount(),
                order.total().currency().getCurrencyCode(), order.createdAt(), order.updatedAt());
    }
}

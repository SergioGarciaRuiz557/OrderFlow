package com.orderflow.order.application.model;

import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Technology-independent result returned by Order application use cases.
 *
 * <p>The REST adapter maps this view to JSON, but the type itself has no web annotations and can be
 * reused by future inbound adapters.</p>
 *
 * @param orderId aggregate identity
 * @param status current lifecycle state
 * @param total calculated decimal total
 * @param currency ISO currency code
 * @param createdAt aggregate creation time
 * @param updatedAt latest transition time
 */
public record OrderView(UUID orderId, OrderStatus status, BigDecimal total, String currency,
                        Instant createdAt, Instant updatedAt) {
    /**
     * Creates an application result from a domain aggregate without exposing domain value objects.
     *
     * @param order source aggregate
     * @return flattened application view
     */
    public static OrderView from(Order order) {
        return new OrderView(order.id().value(), order.status(), order.total().amount(),
                order.total().currency().getCurrencyCode(), order.createdAt(), order.updatedAt());
    }
}

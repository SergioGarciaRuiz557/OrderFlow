package com.orderflow.order.application.exception;

import com.orderflow.order.domain.model.OrderId;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(OrderId orderId) {
        super("Order not found: " + orderId);
    }
}

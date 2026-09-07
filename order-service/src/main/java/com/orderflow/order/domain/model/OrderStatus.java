package com.orderflow.order.domain.model;

public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVATION_PENDING,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    CANCELLATION_PENDING,
    CANCELLED
}

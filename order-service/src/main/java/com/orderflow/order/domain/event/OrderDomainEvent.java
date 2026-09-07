package com.orderflow.order.domain.event;

import com.orderflow.order.domain.model.OrderId;

import java.time.Instant;

public interface OrderDomainEvent {
    OrderId orderId();

    Instant occurredAt();
}

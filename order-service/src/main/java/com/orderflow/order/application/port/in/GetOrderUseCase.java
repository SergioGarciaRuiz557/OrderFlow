package com.orderflow.order.application.port.in;

import com.orderflow.order.application.model.OrderView;

import java.util.UUID;

public interface GetOrderUseCase {
    OrderView getById(UUID orderId);
}

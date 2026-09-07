package com.orderflow.order.application.port.in;

import com.orderflow.order.application.model.OrderView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CreateOrderUseCase {
    OrderView create(CreateOrderCommand command);

    record CreateOrderCommand(UUID customerId, List<CreateOrderItem> items, String paymentMethodId) {
    }

    record CreateOrderItem(String productId, int quantity, BigDecimal unitPrice) {
    }
}

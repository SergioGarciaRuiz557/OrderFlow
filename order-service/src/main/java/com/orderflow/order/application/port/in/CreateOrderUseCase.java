package com.orderflow.order.application.port.in;

import com.orderflow.order.application.model.OrderView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Inbound boundary for creating and starting the lifecycle of a local order. */
public interface CreateOrderUseCase {
    /**
     * Creates, prices, persists, and requests inventory for an order.
     *
     * @param command caller data required to create the aggregate
     * @return representation of the persisted order
     */
    OrderView create(CreateOrderCommand command);

    /**
     * Immutable application command independent of HTTP request types.
     *
     * @param customerId customer placing the order
     * @param items non-empty requested products
     * @param paymentMethodId payment reference for later authorization
     */
    record CreateOrderCommand(UUID customerId, List<CreateOrderItem> items, String paymentMethodId) {
    }

    /**
     * One primitive application-command line that will be converted to domain value objects.
     *
     * @param productId product catalogue reference
     * @param quantity requested unit count
     * @param unitPrice price for one unit
     */
    record CreateOrderItem(String productId, int quantity, BigDecimal unitPrice) {
    }
}

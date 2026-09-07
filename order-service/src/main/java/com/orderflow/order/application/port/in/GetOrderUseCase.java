package com.orderflow.order.application.port.in;

import com.orderflow.order.application.model.OrderView;

import java.util.UUID;

/** Inbound query boundary for retrieving the current representation of an order. */
public interface GetOrderUseCase {
    /**
     * Loads one aggregate by its external UUID.
     *
     * @param orderId requested order UUID
     * @return current order view
     * @throws com.orderflow.order.application.exception.OrderNotFoundException if no order exists
     */
    OrderView getById(UUID orderId);
}

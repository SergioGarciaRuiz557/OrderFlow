package com.orderflow.order.application.port.out;

import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;

import java.util.Optional;

/**
 * Outbound persistence boundary expressed only in domain types.
 *
 * <p>The port deliberately does not extend Spring Data. This lets application services operate
 * without knowing whether storage uses JPA, another database, or an in-memory test double.</p>
 */
public interface OrderRepository {
    /**
     * Inserts or updates the complete aggregate.
     *
     * @param order aggregate to persist
     * @return rehydrated saved aggregate, including its current persistence version
     */
    Order save(Order order);

    /**
     * Finds an aggregate without converting absence into an infrastructure exception.
     *
     * @param orderId requested identity
     * @return aggregate when present
     */
    Optional<Order> findById(OrderId orderId);
}

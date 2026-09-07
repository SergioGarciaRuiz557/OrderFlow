package com.orderflow.order.application.service;

import com.orderflow.order.application.model.OrderView;
import com.orderflow.order.application.port.in.GetOrderUseCase;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read-only implementation of the order retrieval use case. */
@Service
public class GetOrderService implements GetOrderUseCase {
    /** Persistence boundary used to load the aggregate. */
    private final OrderRepository repository;

    /**
     * Creates the query service.
     *
     * @param repository aggregate persistence port
     */
    public GetOrderService(OrderRepository repository) {
        this.repository = repository;
    }

    /**
     * Loads an order and flattens it into an adapter-independent application view.
     *
     * @param orderId external UUID supplied by the inbound adapter
     * @return current representation
     */
    @Override
    @Transactional(readOnly = true)
    public OrderView getById(UUID orderId) {
        return OrderView.from(OrderApplicationSupport.load(repository, new OrderId(orderId)));
    }
}

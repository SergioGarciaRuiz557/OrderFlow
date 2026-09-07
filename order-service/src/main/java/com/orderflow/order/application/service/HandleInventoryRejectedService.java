package com.orderflow.order.application.service;

import com.orderflow.order.application.port.in.HandleInventoryRejectedUseCase;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Handles a failed inventory reservation by cancelling the referenced order. */
@Service
public class HandleInventoryRejectedService implements HandleInventoryRejectedUseCase {
    /** Aggregate persistence port. */
    private final OrderRepository repository;
    /** Domain-event publication port. */
    private final IntegrationMessagePublisher publisher;
    /** Business-time provider. */
    private final ClockProvider clock;

    /**
     * Creates the rejected-inventory callback service.
     *
     * @param repository aggregate persistence port
     * @param publisher domain-event publication port
     * @param clock business-time port
     */
    public HandleInventoryRejectedService(OrderRepository repository, IntegrationMessagePublisher publisher, ClockProvider clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Loads, rejects, saves, and publishes the cancellation facts in one transaction.
     *
     * @param orderId order referenced by the inventory result
     * @param reason inventory rejection explanation
     */
    @Override
    @Transactional
    public void handle(UUID orderId, String reason) {
        Order order = OrderApplicationSupport.load(repository, new OrderId(orderId));
        order.markInventoryRejected(reason, clock.now());
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
    }
}

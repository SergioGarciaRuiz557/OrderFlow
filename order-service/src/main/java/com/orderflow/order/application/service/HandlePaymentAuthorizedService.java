package com.orderflow.order.application.service;

import com.orderflow.order.application.port.in.HandlePaymentAuthorizedUseCase;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Handles successful payment authorization by confirming the referenced order. */
@Service
public class HandlePaymentAuthorizedService implements HandlePaymentAuthorizedUseCase {
    /** Aggregate persistence port. */
    private final OrderRepository repository;
    /** Domain-event publication port. */
    private final IntegrationMessagePublisher publisher;
    /** Business-time provider. */
    private final ClockProvider clock;

    /**
     * Creates the successful-payment callback service.
     *
     * @param repository aggregate persistence port
     * @param publisher domain-event publication port
     * @param clock business-time port
     */
    public HandlePaymentAuthorizedService(OrderRepository repository, IntegrationMessagePublisher publisher, ClockProvider clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Loads the order, delegates confirmation rules to the aggregate, persists, and publishes.
     *
     * @param orderId order referenced by the payment result
     */
    @Override
    @Transactional
    public void handle(UUID orderId) {
        Order order = OrderApplicationSupport.load(repository, new OrderId(orderId));
        order.markPaymentAuthorized(clock.now());
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
    }
}

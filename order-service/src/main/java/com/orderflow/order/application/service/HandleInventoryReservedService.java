package com.orderflow.order.application.service;

import com.orderflow.order.application.port.in.HandleInventoryReservedUseCase;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles a successful inventory callback and starts the next saga step.
 *
 * <p>The aggregate first records that inventory is reserved. Only then can it accept the request for
 * payment authorization, protecting the required lifecycle ordering.</p>
 */
@Service
public class HandleInventoryReservedService implements HandleInventoryReservedUseCase {
    /** Aggregate persistence port. */
    private final OrderRepository repository;
    /** Domain-event publication port. */
    private final IntegrationMessagePublisher publisher;
    /** Business-time provider. */
    private final ClockProvider clock;

    /**
     * Creates the successful-inventory callback service.
     *
     * @param repository aggregate persistence port
     * @param publisher domain-event publication port
     * @param clock business-time port
     */
    public HandleInventoryReservedService(OrderRepository repository, IntegrationMessagePublisher publisher, ClockProvider clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Advances the referenced order through inventory-reserved to payment-pending and persists both
     * domain facts atomically.
     *
     * @param orderId order referenced by the inventory result
     */
    @Override
    @Transactional
    public void handle(UUID orderId) {
        Order order = OrderApplicationSupport.load(repository, new OrderId(orderId));
        // Both consecutive transitions belong to one callback and share its processing time.
        Instant now = clock.now();
        order.markInventoryReserved(now);
        order.requestPaymentAuthorization(now);
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
    }
}

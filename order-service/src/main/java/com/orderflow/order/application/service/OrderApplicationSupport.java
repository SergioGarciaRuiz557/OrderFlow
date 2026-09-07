package com.orderflow.order.application.service;

import com.orderflow.order.application.exception.OrderNotFoundException;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;

/**
 * Shared package-private orchestration operations used by command services.
 *
 * <p>This is not an application port because it is an implementation detail, not a capability
 * exposed across an architectural boundary.</p>
 */
final class OrderApplicationSupport {
    /** Prevents instantiation of this stateless utility class. */
    private OrderApplicationSupport() {
    }

    /**
     * Loads an aggregate and turns an empty repository result into the application-level exception.
     *
     * @param repository persistence boundary
     * @param id requested aggregate identity
     * @return loaded aggregate
     * @throws OrderNotFoundException when the repository has no matching aggregate
     */
    static Order load(OrderRepository repository, OrderId id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Persists state before handing newly produced events to the outbound publisher.
     *
     * <p>Calling {@link Order#pullDomainEvents()} after save ensures events are not published for a
     * persistence operation that failed. The surrounding service transaction can roll back if the
     * publisher itself fails.</p>
     *
     * @param repository aggregate persistence boundary
     * @param publisher technology-neutral event delivery boundary
     * @param order changed aggregate
     */
    static void saveAndPublish(OrderRepository repository, IntegrationMessagePublisher publisher, Order order) {
        repository.save(order);
        publisher.publish(order.pullDomainEvents());
    }
}

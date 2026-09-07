package com.orderflow.order.application.service;

import com.orderflow.order.application.model.OrderView;
import com.orderflow.order.application.port.in.CreateOrderUseCase;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderIdGenerator;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.CustomerId;
import com.orderflow.order.domain.model.Money;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderLine;
import com.orderflow.order.domain.model.PaymentMethodId;
import com.orderflow.order.domain.model.ProductId;
import com.orderflow.order.domain.model.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Transactional implementation of {@link CreateOrderUseCase}.
 *
 * <p>The service maps command primitives to validated domain objects, creates the aggregate, asks it
 * to begin inventory reservation, persists it, and publishes its domain facts. Pricing and state
 * transition rules remain inside {@link Order}.</p>
 */
@Service
public class CreateOrderService implements CreateOrderUseCase {
    /** Stores the aggregate through a hexagonal output port. */
    private final OrderRepository repository;
    /** Delivers newly produced domain facts without exposing a transport. */
    private final IntegrationMessagePublisher publisher;
    /** Supplies one deterministic time for creation and the initial request. */
    private final ClockProvider clock;
    /** Supplies a new Order identity. */
    private final OrderIdGenerator idGenerator;

    /**
     * Creates the service with all nondeterministic and infrastructure dependencies injected.
     *
     * @param repository aggregate persistence port
     * @param publisher domain-event publication port
     * @param clock business-time port
     * @param idGenerator identity-generation port
     */
    public CreateOrderService(OrderRepository repository, IntegrationMessagePublisher publisher,
                              ClockProvider clock, OrderIdGenerator idGenerator) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * Creates and persists an order in inventory-reservation-pending state.
     *
     * @param command customer, item, and payment data
     * @return application representation of the created order
     */
    @Override
    @Transactional
    public OrderView create(CreateOrderCommand command) {
        // Conversion invokes value-object constructors, so domain validation does not depend on HTTP.
        List<OrderLine> lines = command.items().stream()
                .map(item -> new OrderLine(new ProductId(item.productId()), new Quantity(item.quantity()),
                        Money.eur(item.unitPrice())))
                .toList();
        // A single instant gives creation and its immediate inventory request a coherent timestamp.
        Instant now = clock.now();
        Order order = Order.create(idGenerator.nextId(), new CustomerId(command.customerId()), lines,
                new PaymentMethodId(command.paymentMethodId()), now);
        order.requestInventoryReservation(now);
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
        return OrderView.from(order);
    }
}

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

@Service
public class CreateOrderService implements CreateOrderUseCase {
    private final OrderRepository repository;
    private final IntegrationMessagePublisher publisher;
    private final ClockProvider clock;
    private final OrderIdGenerator idGenerator;

    public CreateOrderService(OrderRepository repository, IntegrationMessagePublisher publisher,
                              ClockProvider clock, OrderIdGenerator idGenerator) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public OrderView create(CreateOrderCommand command) {
        List<OrderLine> lines = command.items().stream()
                .map(item -> new OrderLine(new ProductId(item.productId()), new Quantity(item.quantity()),
                        Money.eur(item.unitPrice())))
                .toList();
        Instant now = clock.now();
        Order order = Order.create(idGenerator.nextId(), new CustomerId(command.customerId()), lines,
                new PaymentMethodId(command.paymentMethodId()), now);
        order.requestInventoryReservation(now);
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
        return OrderView.from(order);
    }
}

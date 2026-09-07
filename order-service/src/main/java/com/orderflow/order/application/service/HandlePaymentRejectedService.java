package com.orderflow.order.application.service;

import com.orderflow.order.application.port.in.HandlePaymentRejectedUseCase;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HandlePaymentRejectedService implements HandlePaymentRejectedUseCase {
    private final OrderRepository repository;
    private final IntegrationMessagePublisher publisher;
    private final ClockProvider clock;

    public HandlePaymentRejectedService(OrderRepository repository, IntegrationMessagePublisher publisher, ClockProvider clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(UUID orderId, String reason) {
        Order order = OrderApplicationSupport.load(repository, new OrderId(orderId));
        order.markPaymentRejected(reason, clock.now());
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
    }
}

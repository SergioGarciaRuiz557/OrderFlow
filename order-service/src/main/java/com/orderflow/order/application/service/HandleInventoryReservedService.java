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

@Service
public class HandleInventoryReservedService implements HandleInventoryReservedUseCase {
    private final OrderRepository repository;
    private final IntegrationMessagePublisher publisher;
    private final ClockProvider clock;

    public HandleInventoryReservedService(OrderRepository repository, IntegrationMessagePublisher publisher, ClockProvider clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void handle(UUID orderId) {
        Order order = OrderApplicationSupport.load(repository, new OrderId(orderId));
        Instant now = clock.now();
        order.markInventoryReserved(now);
        order.requestPaymentAuthorization(now);
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
    }
}

package com.orderflow.order.application.service;

import com.orderflow.order.application.exception.OrderNotFoundException;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;

final class OrderApplicationSupport {
    private OrderApplicationSupport() {
    }

    static Order load(OrderRepository repository, OrderId id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    static void saveAndPublish(OrderRepository repository, IntegrationMessagePublisher publisher, Order order) {
        repository.save(order);
        publisher.publish(order.pullDomainEvents());
    }
}

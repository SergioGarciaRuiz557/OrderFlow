package com.orderflow.order.application.service;

import com.orderflow.order.application.model.OrderView;
import com.orderflow.order.application.port.in.GetOrderUseCase;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetOrderService implements GetOrderUseCase {
    private final OrderRepository repository;

    public GetOrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderView getById(UUID orderId) {
        return OrderView.from(OrderApplicationSupport.load(repository, new OrderId(orderId)));
    }
}

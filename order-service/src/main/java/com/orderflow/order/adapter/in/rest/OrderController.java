package com.orderflow.order.adapter.in.rest;

import com.orderflow.order.application.port.in.CreateOrderUseCase;
import com.orderflow.order.application.port.in.GetOrderUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final CreateOrderUseCase createOrder;
    private final GetOrderUseCase getOrder;

    public OrderController(CreateOrderUseCase createOrder, GetOrderUseCase getOrder) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        var command = new CreateOrderUseCase.CreateOrderCommand(
                request.customerId(),
                request.items().stream()
                        .map(item -> new CreateOrderUseCase.CreateOrderItem(
                                item.productId(), item.quantity(), item.unitPrice()))
                        .toList(),
                request.paymentMethodId());
        OrderResponse response = OrderResponse.from(createOrder.create(command));
        return ResponseEntity.created(URI.create("/api/orders/" + response.orderId())).body(response);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return OrderResponse.from(getOrder.getById(orderId));
    }
}

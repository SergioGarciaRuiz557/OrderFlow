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

/**
 * HTTP inbound adapter for creating and retrieving orders.
 *
 * <p>The controller owns only transport concerns: annotations, DTO mapping, use-case invocation, and
 * HTTP response construction. It contains no pricing or lifecycle rules.</p>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    /** Creation input port implemented by the application layer. */
    private final CreateOrderUseCase createOrder;
    /** Retrieval input port implemented by the application layer. */
    private final GetOrderUseCase getOrder;

    /**
     * Creates the HTTP adapter with its two application capabilities.
     *
     * @param createOrder order-creation boundary
     * @param getOrder order-query boundary
     */
    public OrderController(CreateOrderUseCase createOrder, GetOrderUseCase getOrder) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
    }

    /**
     * Validates and maps a creation request, then returns the new resource and its location.
     *
     * @param request Bean-validated JSON body
     * @return {@code 201 Created} response with a {@code Location} header
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        // Transport DTOs stop here; the application receives its own command type.
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

    /**
     * Retrieves the current state of one order.
     *
     * @param orderId UUID parsed by Spring from the path segment
     * @return current order representation
     */
    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return OrderResponse.from(getOrder.getById(orderId));
    }
}

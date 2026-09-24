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
 * Adaptador HTTP de entrada para crear y consultar pedidos.
 *
 * <p>El controlador solo es responsable de cuestiones de transporte: anotaciones, mapeo de DTO, invocación de casos de uso
 * y construcción de respuestas HTTP. No contiene reglas de valoración ni del ciclo de vida.</p>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    /** Puerto de entrada de creación que implementa la capa de aplicación. */
    private final CreateOrderUseCase createOrder;
    /** Puerto de entrada de consulta que implementa la capa de aplicación. */
    private final GetOrderUseCase getOrder;

    /**
     * Crea el adaptador HTTP con sus dos capacidades de aplicación.
     *
     * @param createOrder límite de creación de pedidos
     * @param getOrder límite de consulta de pedidos
     */
    public OrderController(CreateOrderUseCase createOrder, GetOrderUseCase getOrder) {
        this.createOrder = createOrder;
        this.getOrder = getOrder;
    }

    /**
     * Valida y mapea una petición de creación y, a continuación, devuelve el recurso nuevo y su ubicación.
     *
     * @param request cuerpo JSON validado por Bean Validation
     * @return respuesta {@code 201 Created} con una cabecera {@code Location}
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        // Los DTO de transporte terminan aquí; la aplicación recibe su propio tipo de comando.
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
     * Consulta el estado actual de un pedido.
     *
     * @param orderId UUID que Spring analiza a partir del segmento de la ruta
     * @return representación actual del pedido
     */
    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return OrderResponse.from(getOrder.getById(orderId));
    }
}

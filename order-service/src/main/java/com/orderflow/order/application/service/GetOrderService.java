package com.orderflow.order.application.service;

import com.orderflow.order.application.model.OrderView;
import com.orderflow.order.application.port.in.GetOrderUseCase;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Implementación de solo lectura del caso de uso de consulta de pedidos. */
@Service
public class GetOrderService implements GetOrderUseCase {
    /** Límite de persistencia utilizado para cargar el agregado. */
    private final OrderRepository repository;

    /**
     * Crea el servicio de consulta.
     *
     * @param repository puerto de persistencia del agregado
     */
    public GetOrderService(OrderRepository repository) {
        this.repository = repository;
    }

    /**
     * Carga un pedido y lo aplana en una vista de la aplicación independiente del adaptador.
     *
     * @param orderId UUID externo proporcionado por el adaptador de entrada
     * @return representación actual
     */
    @Override
    @Transactional(readOnly = true)
    public OrderView getById(UUID orderId) {
        return OrderView.from(OrderApplicationSupport.load(repository, new OrderId(orderId)));
    }
}

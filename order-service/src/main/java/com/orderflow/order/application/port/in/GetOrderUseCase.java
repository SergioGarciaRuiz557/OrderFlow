package com.orderflow.order.application.port.in;

import com.orderflow.order.application.model.OrderView;

import java.util.UUID;

/** Límite de consulta de entrada para recuperar la representación actual de un pedido. */
public interface GetOrderUseCase {
    /**
     * Carga un agregado mediante su UUID externo.
     *
     * @param orderId UUID del pedido solicitado
     * @return vista actual del pedido
     * @throws com.orderflow.order.application.exception.OrderNotFoundException si el pedido no existe
     */
    OrderView getById(UUID orderId);
}

package com.orderflow.order.application.port.in;

import java.util.UUID;

/** Límite de notificación de entrada para el resultado de una reserva de inventario rechazada. */
public interface HandleInventoryRejectedUseCase {
    /**
     * Registra el rechazo del inventario y cancela el pedido.
     *
     * @param orderId pedido al que hace referencia el resultado externo
     * @param reason explicación del rechazo proporcionada por Inventory
     */
    void handle(UUID orderId, String reason);
}

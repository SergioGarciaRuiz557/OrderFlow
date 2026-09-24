package com.orderflow.order.application.port.in;

import java.util.UUID;

/** Límite de notificación de entrada para el resultado correcto de una reserva de inventario. */
public interface HandleInventoryReservedUseCase {
    /**
     * Registra el resultado correcto del inventario y hace avanzar el pedido hasta la autorización del pago.
     *
     * @param orderId pedido al que hace referencia el resultado externo
     */
    void handle(UUID orderId);
}

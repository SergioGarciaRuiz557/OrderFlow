package com.orderflow.order.application.port.in;

import java.util.UUID;

/** Límite de notificación de entrada para una autorización de pago rechazada. */
public interface HandlePaymentRejectedUseCase {
    /**
     * Registra el rechazo del pago e inicia la compensación mediante la liberación del inventario.
     *
     * @param orderId pedido al que hace referencia el resultado externo
     * @param reason explicación del rechazo proporcionada por Payment
     */
    void handle(UUID orderId, String reason);
}

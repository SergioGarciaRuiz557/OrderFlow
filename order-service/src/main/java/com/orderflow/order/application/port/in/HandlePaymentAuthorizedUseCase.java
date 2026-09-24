package com.orderflow.order.application.port.in;

import java.util.UUID;

/** Límite de notificación de entrada para una autorización de pago correcta. */
public interface HandlePaymentAuthorizedUseCase {
    /**
     * Registra el pago correcto y confirma el pedido.
     *
     * @param orderId pedido al que hace referencia el resultado externo
     */
    void handle(UUID orderId);
}

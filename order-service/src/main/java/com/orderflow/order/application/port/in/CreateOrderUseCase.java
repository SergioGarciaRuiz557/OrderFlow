package com.orderflow.order.application.port.in;

import com.orderflow.order.application.model.OrderView;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Límite de entrada para crear e iniciar el ciclo de vida de un pedido local. */
public interface CreateOrderUseCase {
    /**
     * Crea, valora y persiste un pedido, y solicita su inventario.
     *
     * @param command datos del consumidor necesarios para crear el agregado
     * @return representación del pedido persistido
     */
    OrderView create(CreateOrderCommand command);

    /**
     * Comando inmutable de la aplicación independiente de los tipos de petición HTTP.
     *
     * @param customerId cliente que realiza el pedido
     * @param items productos solicitados; no puede estar vacío
     * @param paymentMethodId referencia de pago para su posterior autorización
     */
    record CreateOrderCommand(UUID customerId, List<CreateOrderItem> items, String paymentMethodId) {
    }

    /**
     * Una línea primitiva del comando de aplicación que se convertirá en objetos de valor del dominio.
     *
     * @param productId referencia del catálogo de productos
     * @param quantity número de unidades solicitado
     * @param unitPrice precio de una unidad
     */
    record CreateOrderItem(String productId, int quantity, BigDecimal unitPrice) {
    }
}

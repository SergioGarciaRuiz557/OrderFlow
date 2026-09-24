package com.orderflow.order.application.port.out;

import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;

import java.util.Optional;

/**
 * Límite de persistencia de salida expresado únicamente mediante tipos del dominio.
 *
 * <p>El puerto deliberadamente no extiende Spring Data. Esto permite que los servicios de aplicación funcionen
 * sin saber si el almacenamiento utiliza JPA, otra base de datos o un doble de pruebas en memoria.</p>
 */
public interface OrderRepository {
    /**
     * Inserta o actualiza el agregado completo.
     *
     * @param order agregado que se persistirá
     * @return agregado guardado y rehidratado, incluida su versión de persistencia actual
     */
    Order save(Order order);

    /**
     * Busca un agregado sin convertir su ausencia en una excepción de infraestructura.
     *
     * @param orderId identidad solicitada
     * @return agregado cuando está presente
     */
    Optional<Order> findById(OrderId orderId);
}

package com.orderflow.order.application.port.out;

import com.orderflow.order.domain.model.OrderId;

/** Proporciona identidades nuevas del agregado y permite generadores deterministas en las pruebas unitarias. */
public interface OrderIdGenerator {
    /**
     * Genera la identidad de un agregado nuevo.
     *
     * @return identidad que no se ha asignado previamente a un pedido
     */
    OrderId nextId();
}

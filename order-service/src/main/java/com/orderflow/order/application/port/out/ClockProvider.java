package com.orderflow.order.application.port.out;

import java.time.Instant;

/** Proporciona el tiempo de negocio sin acoplar los servicios de aplicación al reloj del sistema. */
public interface ClockProvider {
    /**
     * Obtiene el tiempo de negocio actual.
     *
     * @return instante actual para las marcas temporales de creación o transición del agregado
     */
    Instant now();
}

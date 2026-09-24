package com.orderflow.inventory.application.port.`out`

import java.time.Instant

/**
 * Puerto de salida de la aplicación que proporciona la hora actual de referencia.
 *
 * Las operaciones de dominio reciben explícitamente las marcas temporales, lo que mantiene el
 * dominio determinista. Producción utiliza un reloj del sistema en UTC, mientras que las pruebas
 * pueden proporcionar un valor fijo sin simulaciones estáticas.
 */
fun interface ClockProvider {
    /**
     * Obtiene el instante actual para las marcas temporales del ciclo de vida de una reserva.
     *
     * @return hora actual de la fuente de reloj configurada.
     */
    fun now(): Instant
}

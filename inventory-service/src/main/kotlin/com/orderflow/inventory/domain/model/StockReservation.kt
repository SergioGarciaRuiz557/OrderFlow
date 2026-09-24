package com.orderflow.inventory.domain.model

import java.time.Instant

/**
 * Estado del ciclo de vida de una reserva de existencias.
 *
 * Esta máquina de estados deliberadamente pequeña permite la asignación y la compensación sin que
 * una reserva liberada pueda volver a estar activa.
 */
enum class ReservationStatus {
    /** Las existencias están asignadas actualmente al pedido asociado. */
    ACTIVE,

    /** Las existencias asignadas han vuelto a estar disponibles y no pueden devolverse por segunda vez. */
    RELEASED,
}

/**
 * Entidad de dominio que registra una asignación trazable de existencias a un pedido.
 *
 * Una reserva pertenece a un agregado [InventoryItem] y nunca se persiste ni modifica de forma
 * independiente. Las marcas temporales y el estado conservan el historial necesario para distinguir
 * una asignación activa de una compensación que ya se ha aplicado.
 *
 * @property id identificador estable utilizado por los comandos de liberación y la persistencia.
 * @property orderId pedido al que se asignaron las unidades.
 * @property quantity número positivo de unidades asignadas.
 * @property status estado actual del ciclo de vida.
 * @property reservedAt instante en el que se aceptó la asignación.
 * @property releasedAt instante en el que se devolvió la asignación, o `null` mientras esté activa.
 */
data class StockReservation(
    val id: ReservationId,
    val orderId: OrderId,
    val quantity: Quantity,
    val status: ReservationStatus,
    val reservedAt: Instant,
    val releasedAt: Instant?,
) {
    /**
     * Garantiza que el estado del ciclo de vida y los datos temporales no se contradigan.
     *
     * Las reservas activas no deben tener una marca temporal de liberación, mientras que las reservas
     * liberadas deben tenerla. La condición se evalúa tanto para las instancias recién creadas como
     * para las reconstituidas.
     */
    init {
        require((status == ReservationStatus.ACTIVE) == (releasedAt == null)) {
            "Only released reservations may have a release time"
        }
    }

    /**
     * Cambia esta reserva a [ReservationStatus.RELEASED].
     *
     * La operación es idempotente. Liberar una reserva ya liberada devuelve la misma instancia,
     * conserva la marca temporal de liberación original y evita que los consumidores representen
     * una segunda reposición de existencias.
     *
     * @param at instante de referencia en el que se produce la primera liberación.
     * @return una copia liberada de una reserva activa, o esta instancia si ya estaba liberada.
     */
    fun release(at: Instant): StockReservation = when (status) {
        ReservationStatus.ACTIVE -> copy(status = ReservationStatus.RELEASED, releasedAt = at)
        ReservationStatus.RELEASED -> this
    }

    /** Operaciones de factoría que crean estados válidos del ciclo de vida de una reserva. */
    companion object {
        /**
         * Crea una reserva activa recién aceptada.
         *
         * La marca temporal de liberación se omite deliberadamente porque aún no se ha producido ninguna compensación.
         *
         * @param id identificador generado por la capa de aplicación para este intento de reserva.
         * @param orderId pedido que recibe la asignación.
         * @param quantity unidades asignadas al pedido.
         * @param reservedAt instante de referencia de la aceptación.
         * @return una reserva activa válida.
         */
        fun active(
            id: ReservationId,
            orderId: OrderId,
            quantity: Quantity,
            reservedAt: Instant,
        ): StockReservation = StockReservation(
            id = id,
            orderId = orderId,
            quantity = quantity,
            status = ReservationStatus.ACTIVE,
            reservedAt = reservedAt,
            releasedAt = null,
        )
    }
}

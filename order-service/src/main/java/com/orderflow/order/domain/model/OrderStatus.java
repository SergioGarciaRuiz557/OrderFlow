package com.orderflow.order.domain.model;

/**
 * Estado persistido del ciclo de vida de un {@link Order}.
 *
 * <p>El enum solo describe los estados posibles. Los métodos del agregado deciden qué transiciones son
 * válidas y son el único código de producción que puede cambiar el estado de un pedido.</p>
 */
public enum OrderStatus {
    /** El agregado existe, pero aún no se ha solicitado el inventario. */
    PENDING,
    /** Una solicitud de reserva de inventario espera un resultado. */
    INVENTORY_RESERVATION_PENDING,
    /** El inventario está reservado, pero aún no se ha solicitado el pago. */
    INVENTORY_RESERVED,
    /** La autorización del pago espera un resultado. */
    PAYMENT_PENDING,
    /** El inventario y el pago se han completado correctamente; este es un estado terminal satisfactorio. */
    CONFIRMED,
    /** El trabajo de compensación o cancelación debe completarse antes de la cancelación terminal. */
    CANCELLATION_PENDING,
    /** El pedido se ha cancelado; este es un estado terminal. */
    CANCELLED
}

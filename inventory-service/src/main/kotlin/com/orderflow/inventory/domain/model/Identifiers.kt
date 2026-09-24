package com.orderflow.inventory.domain.model

import java.util.UUID

/**
 * Identificador fuertemente tipado de un producto cuyas existencias gestiona este contexto delimitado.
 *
 * Una value class evita que un identificador de producto se intercambie accidentalmente con otro
 * identificador basado en String y normalmente evita una asignación adicional durante la ejecución.
 *
 * @property value identificador externo del producto utilizado por REST, la persistencia y los mensajes futuros.
 * @throws IllegalArgumentException cuando [value] está vacío.
 */
@JvmInline
value class ProductId(val value: String) {
    /** Valida el identificador en la frontera del modelo de dominio. */
    init {
        require(value.isNotBlank()) { "Product id must not be blank" }
    }
}

/**
 * Identificador fuertemente tipado del pedido que solicita una reserva de existencias.
 *
 * El agregado de inventario utiliza este identificador como clave de idempotencia de negocio: un
 * pedido concreto puede crear como máximo una reserva para un producto.
 *
 * @property value identificador asignado por el contexto delimitado Order.
 * @throws IllegalArgumentException cuando [value] está vacío.
 */
@JvmInline
value class OrderId(val value: String) {
    /** Rechaza identificadores que no pueden representar un pedido real. */
    init {
        require(value.isNotBlank()) { "Order id must not be blank" }
    }
}

/**
 * Identificador único global de una [StockReservation].
 *
 * Encapsular [UUID] hace explícito en las API el tipo de identificador que aceptan y evita mezclar
 * identificadores de reserva con identificadores de producto o de pedido.
 *
 * @property value UUID persistido como clave primaria del registro de reserva.
 */
@JvmInline
value class ReservationId(val value: UUID) {
    /** Operaciones de factoría para identificadores de reserva. */
    companion object {
        /**
         * Crea un identificador aleatorio nuevo para un nuevo intento de reserva.
         *
         * Las reservas idempotentes existentes conservan su identificador original; solo se utiliza
         * un valor generado si el agregado acepta el intento como una reserva realmente nueva.
         */
        fun new(): ReservationId = ReservationId(UUID.randomUUID())
    }
}

/**
 * Número positivo de unidades de producto solicitado por una reserva.
 *
 * Las cantidades nulas o negativas no son válidas por construcción, por lo que las operaciones del
 * agregado no necesitan repetir esa comprobación. Las existencias disponibles se representan por
 * separado mediante un `Int`, porque cero es válido para un nivel de existencias, pero no para una
 * solicitud de reserva.
 *
 * @property value número de unidades que se reservarán.
 * @throws IllegalArgumentException cuando [value] es cero o negativo.
 */
@JvmInline
value class Quantity(val value: Int) {
    /** Aplica la invariante de reserva positiva cuando el valor entra en el dominio. */
    init {
        require(value > 0) { "Reservation quantity must be positive" }
    }
}

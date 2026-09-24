package com.orderflow.inventory.domain.model

import java.time.Instant

/**
 * Raíz de agregado responsable de las existencias y el historial de reservas de un producto.
 *
 * Toda la aritmética de existencias se realiza mediante este tipo. Los consumidores no pueden
 * reducir las existencias ni modificar una [StockReservation] de forma independiente, lo que mantiene
 * coherentes la disponibilidad y el historial de reservas. Las operaciones de dominio devuelven
 * nuevas instancias del agregado para que el estado cargado previamente siga siendo una instantánea
 * inmutable adecuada para el control de concurrencia optimista.
 *
 * El constructor es privado para obligar a crear instancias mediante [create] o a reconstruirlas
 * desde la persistencia mediante [reconstitute]. Ambas rutas ejecutan las invariantes del agregado
 * en el bloque `init`.
 *
 * @property productId producto representado por este agregado.
 * @property availableQuantity unidades disponibles actualmente para nuevas reservas; nunca es negativo.
 * @property reservations historial inmutable de reservas activas y liberadas del producto.
 * @property version token de concurrencia de persistencia, o `null` antes de almacenar el agregado por primera vez.
 */
@ConsistentCopyVisibility
data class InventoryItem private constructor(
    val productId: ProductId,
    val availableQuantity: Int,
    val reservations: List<StockReservation>,
    val version: Long?,
) {
    /**
     * Valida las invariantes que deben cumplirse en todos los estados del agregado, incluidos los datos cargados de la base de datos.
     *
     * La segunda condición convierte el par producto/pedido en la frontera de idempotencia de negocio.
     * Una reserva liberada permanece en el historial, por lo que el mismo pedido no puede crear
     * accidentalmente otra reserva posterior para el mismo producto.
     */
    init {
        require(availableQuantity >= 0) { "Available stock cannot be negative" }
        require(reservations.distinctBy { it.orderId }.size == reservations.size) {
            "An order may have only one reservation for a product"
        }
    }

    /**
     * Sustituye las existencias disponibles actualmente para nuevas reservas.
     *
     * Esta operación sirve a la API de administración y desarrollo. Modifica directamente la
     * disponibilidad, pero conserva deliberadamente el historial de reservas. Al persistir el
     * agregado devuelto se utiliza la [version] existente, por lo que se siguen detectando los
     * cambios concurrentes.
     *
     * @param quantity nuevo número de unidades disponibles para reserva; cero es válido.
     * @return un nuevo agregado con la disponibilidad solicitada.
     * @throws IllegalArgumentException cuando [quantity] es negativo.
     */
    fun setAvailableQuantity(quantity: Int): InventoryItem {
        require(quantity >= 0) { "Available stock cannot be negative" }
        return copy(availableQuantity = quantity)
    }

    /**
     * Intenta asignar existencias a un pedido aplicando las reglas de disponibilidad e idempotencia.
     *
     * El orden de evaluación es intencionado:
     * 1. Las reservas existentes del pedido se procesan antes de comprobar las existencias, lo que
     *    permite que una solicitud duplicada exacta devuelva su reserva satisfactoria original aunque
     *    las existencias se hayan agotado.
     * 2. Se rechaza una solicitud diferente para el mismo pedido a fin de evitar asignaciones duplicadas.
     * 3. Las solicitudes nuevas se rechazan cuando la cantidad solicitada supera la disponibilidad.
     * 4. Las solicitudes aceptadas reducen la disponibilidad y añaden su registro de reserva de forma
     *    atómica en el estado devuelto del agregado.
     *
     * Ningún valor `null` ni excepción representa un rechazo de negocio normal; todas las rutas
     * devuelven un [ReservationResult] explícito.
     *
     * @param reservationId identificador candidato para una nueva reserva.
     * @param orderId pedido que solicita las existencias.
     * @param quantity número positivo de unidades solicitadas.
     * @param at instante de referencia utilizado si se acepta una nueva reserva.
     * @return [ReservationResult.Reserved] para un resultado satisfactorio nuevo o repetido de forma
     * idempotente; en caso contrario, [ReservationResult.Rejected] con un motivo de negocio.
     */
    fun reserve(
        reservationId: ReservationId,
        orderId: OrderId,
        quantity: Quantity,
        at: Instant,
    ): ReservationResult {
        // La identidad del pedido es la primera protección porque define la idempotencia de negocio.
        val existing = reservations.firstOrNull { it.orderId == orderId }
        if (existing != null) {
            return if (existing.status == ReservationStatus.ACTIVE && existing.quantity == quantity) {
                ReservationResult.Reserved(this, existing, wasAlreadyReserved = true)
            } else {
                ReservationResult.Rejected(
                    reason = ReservationRejectionReason.ORDER_ALREADY_HAS_RESERVATION,
                    availableQuantity = availableQuantity,
                )
            }
        }

        // Las existencias se comprueban antes de restar, por lo que nunca puede construirse un estado negativo.
        if (quantity.value > availableQuantity) {
            return ReservationResult.Rejected(
                reason = ReservationRejectionReason.INSUFFICIENT_STOCK,
                availableQuantity = availableQuantity,
            )
        }

        // La disponibilidad y la trazabilidad cambian a la vez en la nueva instantánea del agregado.
        val reservation = StockReservation.active(reservationId, orderId, quantity, at)
        return ReservationResult.Reserved(
            inventoryItem = copy(
                availableQuantity = availableQuantity - quantity.value,
                reservations = reservations + reservation,
            ),
            reservation = reservation,
            wasAlreadyReserved = false,
        )
    }

    /**
     * Libera una reserva y repone sus unidades exactamente una vez.
     *
     * Los identificadores desconocidos y las liberaciones repetidas son resultados explícitos
     * diferentes. Esta distinción permite que los adaptadores de entrada definan una política de
     * idempotencia sin ocultar comandos incorrectos. En una reserva activa, tanto el estado de la
     * reserva como las existencias disponibles se actualizan en un único estado nuevo del agregado.
     *
     * @param reservationId identificador de la reserva que se compensará.
     * @param at instante de referencia registrado para una primera liberación.
     * @return un [ReleaseResult] exhaustivo que describe la liberación, su duplicación o su ausencia.
     */
    fun release(reservationId: ReservationId, at: Instant): ReleaseResult {
        val reservation = reservations.firstOrNull { it.id == reservationId }
            ?: return ReleaseResult.ReservationNotFound(reservationId)

        if (reservation.status == ReservationStatus.RELEASED) {
            return ReleaseResult.AlreadyReleased(this, reservation)
        }

        // El mapeo sustituye únicamente la entidad propia que se libera y conserva el historial completo.
        val released = reservation.release(at)
        return ReleaseResult.Released(
            inventoryItem = copy(
                availableQuantity = availableQuantity + reservation.quantity.value,
                reservations = reservations.map { if (it.id == reservationId) released else it },
            ),
            reservation = released,
        )
    }

    /** Rutas de construcción controladas para instancias nuevas y persistidas del agregado. */
    companion object {
        /**
         * Crea un inventario que aún no se ha persistido y no tiene historial de reservas.
         *
         * @param productId producto que se gestionará.
         * @param availableQuantity existencias iniciales; se permite cero.
         * @return un nuevo agregado cuya [version] es `null` hasta que la persistencia le asigne una.
         */
        fun create(productId: ProductId, availableQuantity: Int): InventoryItem = InventoryItem(
            productId = productId,
            availableQuantity = availableQuantity,
            reservations = emptyList(),
            version = null,
        )

        /**
         * Reconstruye un agregado desde la persistencia sin exponer un constructor público sin restricciones.
         *
         * Una copia defensiva de la lista evita que una colección mutable de persistencia pase a formar
         * parte del estado del dominio. Las invariantes del constructor validan los datos almacenados
         * durante la reconstrucción.
         *
         * @param productId identificador persistido del producto.
         * @param availableQuantity existencias disponibles persistidas.
         * @param reservations historial completo de reservas persistidas del producto.
         * @param version versión de bloqueo optimista leída de PostgreSQL.
         * @return la instantánea reconstruida del agregado.
         */
        fun reconstitute(
            productId: ProductId,
            availableQuantity: Int,
            reservations: List<StockReservation>,
            version: Long,
        ): InventoryItem = InventoryItem(productId, availableQuantity, reservations.toList(), version)
    }
}

/** Motivos de negocio para rechazar una solicitud de reserva de inventario. */
enum class ReservationRejectionReason {
    /** La cantidad solicitada es superior a la disponibilidad actual del agregado. */
    INSUFFICIENT_STOCK,

    /** El pedido ya posee una reserva que no corresponde a la misma solicitud idempotente. */
    ORDER_ALREADY_HAS_RESERVATION,

    /** No se ha preparado ningún agregado de inventario para el producto solicitado. */
    INVENTORY_ITEM_NOT_FOUND,
}

/**
 * Resultado exhaustivo de negocio de un intento de reserva.
 *
 * Esta jerarquía sellada garantiza que los consumidores procesen de forma consciente los resultados
 * aceptados y rechazados. Los fallos técnicos de persistencia no se incluyen porque no son rechazos
 * de negocio.
 */
sealed interface ReservationResult {
    /**
     * Resultado satisfactorio de una reserva.
     *
     * @property inventoryItem estado del agregado que contiene la reserva aceptada.
     * @property reservation reserva idempotente recién creada o ya existente.
     * @property wasAlreadyReserved `true` cuando no se requiere ningún cambio de estado ni escritura en la base de datos.
     */
    data class Reserved(
        val inventoryItem: InventoryItem,
        val reservation: StockReservation,
        val wasAlreadyReserved: Boolean,
    ) : ReservationResult

    /**
     * Rechazo de negocio esperado que no modifica el estado del agregado.
     *
     * @property reason regla que impidió la asignación.
     * @property availableQuantity existencias observadas durante la evaluación, o `null` cuando el
     * producto no existe y, por tanto, no tiene un nivel de existencias.
     */
    data class Rejected(
        val reason: ReservationRejectionReason,
        val availableQuantity: Int?,
    ) : ReservationResult
}

/**
 * Resultado exhaustivo de negocio de la liberación de una reserva.
 *
 * Las liberaciones repetidas y desconocidas se modelan por separado para que los adaptadores de
 * aplicación puedan ser idempotentes sin aceptar silenciosamente un identificador de reserva no válido.
 */
sealed interface ReleaseResult {
    /**
     * Primera liberación satisfactoria.
     *
     * @property inventoryItem agregado después de reponer las existencias.
     * @property reservation reserva que ha pasado al estado liberado.
     */
    data class Released(
        val inventoryItem: InventoryItem,
        val reservation: StockReservation,
    ) : ReleaseResult

    /**
     * Resultado idempotente de una reserva que ya se había liberado.
     *
     * @property inventoryItem estado sin cambios del agregado.
     * @property reservation reserva liberada existente con su instante de liberación original.
     */
    data class AlreadyReleased(
        val inventoryItem: InventoryItem,
        val reservation: StockReservation,
    ) : ReleaseResult

    /**
     * Resultado explícito cuando ningún agregado contiene el identificador de reserva proporcionado.
     *
     * @property reservationId identificador que no se pudo localizar.
     */
    data class ReservationNotFound(val reservationId: ReservationId) : ReleaseResult
}

package com.orderflow.payment.domain.model

import java.time.Instant

/**
 * Ciclo de vida completo e intencionadamente reducido de una autorización de pago.
 *
 * No existe un asignador genérico del estado. El comportamiento de [Payment] controla las
 * transiciones para que los resultados terminales no puedan volver a `PENDING` ni pasar de rechazo
 * a autorización.
 */
enum class PaymentStatus {
    /** Creado y conservado, pero todavía sin una decisión definitiva del proveedor. */
    PENDING,

    /** El proveedor aprobó el importe y proporcionó una referencia de autorización rastreable. */
    AUTHORIZED,

    /** El proveedor devolvió un rechazo de negocio definitivo con un motivo. */
    REJECTED,
}

/**
 * Raíz del agregado del contexto delimitado de Pagos.
 *
 * El agregado posee el estado de autorización y garantiza que las referencias del proveedor, los
 * motivos de rechazo, las marcas de tiempo y el estado siempre formen una instantánea coherente. Es
 * inmutable: el comportamiento devuelve un [Payment] nuevo en lugar de mutar campos, lo que impide
 * estados intermedios no válidos y encaja de forma natural con el bloqueo optimista.
 *
 * El constructor principal es privado. Las nuevas instancias de negocio deben usar [pending],
 * mientras que el adaptador de persistencia usa [reconstitute]. Ambas rutas ejecutan los mismos
 * invariantes de `init`. `@ConsistentCopyVisibility` garantiza que el método `copy` generado para la
 * clase de datos siga siendo privado como el constructor, evitando que los llamadores eludan el
 * comportamiento del ciclo de vida.
 *
 * @property id identificador técnico del agregado y clave primaria de la base de datos.
 * @property orderId clave de idempotencia de negocio; solo se permite un pago por pedido.
 * @property amount importe monetario explícito y normalizado solicitado para la autorización.
 * @property paymentMethodId token opaco del método de pago del proveedor.
 * @property status estado actual del ciclo de vida.
 * @property providerReference referencia de autorización del proveedor, presente solo cuando está autorizado.
 * @property failureReason motivo de rechazo de negocio, presente solo cuando está rechazado.
 * @property createdAt instante inmutable en el que se creó el pago pendiente.
 * @property updatedAt instante de la transición satisfactoria de dominio más reciente.
 * @property version token de bloqueo optimista de JPA, o `null` antes de la primera persistencia.
 */
@ConsistentCopyVisibility
data class Payment private constructor(
    val id: PaymentId,
    val orderId: OrderId,
    val amount: Money,
    val paymentMethodId: PaymentMethodId,
    val status: PaymentStatus,
    val providerReference: PaymentProviderReference?,
    val failureReason: PaymentFailureReason?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long?,
) {
    /** Valida los invariantes comunes a las instantáneas nuevas, transitadas y cargadas desde la base de datos. */
    init {
        // Las marcas de tiempo del ciclo de vida deben ser monótonas aunque un llamador proporcione un ClockProvider personalizado.
        require(!updatedAt.isBefore(createdAt)) { "Payment update time cannot precede creation time" }

        // Estas comprobaciones exhaustivas hacen que los campos anulables de resultado solo sean válidos en su estado lógico.
        when (status) {
            PaymentStatus.PENDING -> require(providerReference == null && failureReason == null) {
                "Pending payments cannot contain an authorization outcome"
            }
            PaymentStatus.AUTHORIZED -> require(providerReference != null && failureReason == null) {
                "Authorized payments require only a provider reference"
            }
            PaymentStatus.REJECTED -> require(providerReference == null && failureReason != null) {
                "Rejected payments require only a failure reason"
            }
        }
    }

    /**
     * Aplica la decisión satisfactoria de autorización del proveedor.
     *
     * Solo se puede autorizar un pago pendiente. La instantánea devuelta contiene la referencia del
     * proveedor y el instante de transición, y conserva los datos inmutables de la solicitud y la
     * versión de persistencia. Llamar a este método sobre un pago autorizado o rechazado constituye
     * un error de programación o de estado del dominio.
     *
     * @param reference referencia no vacía del proveedor que acredita la autorización satisfactoria.
     * @param at instante autoritativo de transición proporcionado por el reloj de la aplicación.
     * @return una nueva instantánea autorizada del agregado.
     * @throws IllegalStateException cuando este pago ya no está pendiente.
     * @throws IllegalArgumentException cuando [at] es anterior a [createdAt].
     */
    fun authorize(reference: PaymentProviderReference, at: Instant): Payment {
        // La protección previa a `copy` evita transiciones desde estados terminales y autorizaciones duplicadas.
        check(status == PaymentStatus.PENDING) { "Only a pending payment can be authorized" }
        return copy(
            status = PaymentStatus.AUTHORIZED,
            providerReference = reference,
            updatedAt = at,
        )
    }

    /**
     * Aplica un rechazo de negocio definitivo devuelto por el proveedor.
     *
     * Los fallos técnicos nunca deben llamar a este método; dejan pendiente el pago conservado y se
     * propagan como excepción para que la infraestructura pueda reintentarlo. Al igual que la
     * autorización, el rechazo es una transición terminal y solo puede producirse una vez.
     *
     * @param reason motivo de negocio no vacío que explica la decisión del proveedor.
     * @param at instante autoritativo de transición proporcionado por el reloj de la aplicación.
     * @return una nueva instantánea rechazada del agregado.
     * @throws IllegalStateException cuando este pago ya no está pendiente.
     * @throws IllegalArgumentException cuando [at] es anterior a [createdAt].
     */
    fun reject(reason: PaymentFailureReason, at: Instant): Payment {
        check(status == PaymentStatus.PENDING) { "Only a pending payment can be rejected" }
        return copy(
            status = PaymentStatus.REJECTED,
            failureReason = reason,
            updatedAt = at,
        )
    }

    /**
     * Determina si un reintento entrante representa exactamente la solicitud de negocio original.
     *
     * La identidad del pedido basta para encontrar el registro de idempotencia, pero también deben
     * coincidir el importe y el método de pago. Una solicitud modificada es un conflicto, no un
     * reintento inocuo, y la aplicación la rechaza.
     *
     * @param amount importe proporcionado por el comando repetido.
     * @param paymentMethodId instrumento de pago proporcionado por el comando repetido.
     * @return `true` solo cuando coinciden ambos atributos inmutables de la solicitud.
     */
    fun matches(amount: Money, paymentMethodId: PaymentMethodId): Boolean =
        this.amount == amount && this.paymentMethodId == paymentMethodId

    /** Rutas de construcción controladas para instantáneas nuevas y conservadas del agregado. */
    companion object {
        /**
         * Crea un pago nuevo antes de que exista un resultado definitivo del proveedor.
         *
         * Los campos de resultado comienzan vacíos, ambas marcas de tiempo comparten el instante de
         * creación y [version] permanece como `null` hasta que JPA inserta la fila. La aplicación
         * conserva este estado antes de interpretar un fallo técnico de la pasarela, lo que permite
         * que un reintento posterior encuentre la misma operación de negocio.
         *
         * @return un agregado pendiente válido que todavía no se ha conservado.
         */
        fun pending(
            id: PaymentId,
            orderId: OrderId,
            amount: Money,
            paymentMethodId: PaymentMethodId,
            createdAt: Instant,
        ): Payment = Payment(
            id = id,
            orderId = orderId,
            amount = amount,
            paymentMethodId = paymentMethodId,
            status = PaymentStatus.PENDING,
            providerReference = null,
            failureReason = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            version = null,
        )

        /**
         * Reconstruye un pago a partir de primitivas fiables de persistencia mediante objetos de valor del dominio.
         *
         * No es una puerta trasera que eluda los invariantes: el constructor privado y el bloque
         * `init` validan la coherencia del ciclo de vida y las marcas de tiempo. Se requiere una versión
         * no nula porque esta ruta se destina exclusivamente a filas que ya existen en PostgreSQL.
         *
         * @return representación inmutable en el dominio de una fila de pago conservada.
         * @throws IllegalArgumentException cuando el estado almacenado infringe un invariante del agregado.
         */
        fun reconstitute(
            id: PaymentId,
            orderId: OrderId,
            amount: Money,
            paymentMethodId: PaymentMethodId,
            status: PaymentStatus,
            providerReference: PaymentProviderReference?,
            failureReason: PaymentFailureReason?,
            createdAt: Instant,
            updatedAt: Instant,
            version: Long,
        ): Payment = Payment(
            id,
            orderId,
            amount,
            paymentMethodId,
            status,
            providerReference,
            failureReason,
            createdAt,
            updatedAt,
            version,
        )
    }
}

package com.orderflow.payment.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.UUID

/**
 * Identificador con tipado fuerte de un agregado [Payment].
 *
 * Encapsular el [UUID] sin procesar evita que los identificadores de pago se confundan por accidente
 * con identificadores de pedido u otras cadenas. `@JvmInline` suele evitar la asignación de un
 * envoltorio adicional en tiempo de ejecución y mantiene la seguridad de tipos específica del dominio
 * en las API de Kotlin.
 *
 * @property value UUID único globalmente que se almacena como clave primaria `payments.payment_id`.
 */
@JvmInline
value class PaymentId(val value: UUID) {
    /** Operaciones de fábrica para identificadores de pago. */
    companion object {
        /**
         * Crea un identificador aleatorio para un pago que todavía no se ha conservado.
         *
         * Los reintentos de un pago existente no llaman a este método: vuelven a cargar el agregado
         * original y conservan su identificador. La idempotencia del proveedor usa [OrderId], por lo
         * que también permanece estable si la persistencia local falla antes de poder almacenar un
         * identificador recién generado.
         */
        fun new(): PaymentId = PaymentId(UUID.randomUUID())
    }
}

/**
 * Identificador que el contexto delimitado de Pedidos asigna a la operación de negocio que se paga.
 *
 * Es más que una referencia externa: constituye la clave de idempotencia de negocio para la
 * autorización. Un pedido solo puede poseer una fila de pago y se envía el mismo valor a la pasarela
 * de pago como clave de idempotencia.
 *
 * @property value identificador externo del pedido, conservado sin transformación.
 * @throws IllegalArgumentException cuando [value] está en blanco.
 */
@JvmInline
value class OrderId(val value: String) {
    /** Rechaza los identificadores que no pueden identificar un pedido real. */
    init {
        require(value.isNotBlank()) { "Order id must not be blank" }
    }
}

/**
 * Identificador opaco del instrumento de pago del cliente en el proveedor.
 *
 * El servicio nunca analiza datos de tarjeta o bancarios a partir de este valor. Mantener el token
 * opaco limita el contexto delimitado a coordinar la autorización y evita almacenar datos
 * confidenciales del instrumento.
 *
 * @property value token del método de pago dirigido al proveedor.
 * @throws IllegalArgumentException cuando [value] está en blanco.
 */
@JvmInline
value class PaymentMethodId(val value: String) {
    /** Garantiza que cada solicitud de autorización identifique un instrumento de pago. */
    init {
        require(value.isNotBlank()) { "Payment method id must not be blank" }
    }
}

/**
 * Referencia que devuelve el proveedor de pagos tras una autorización satisfactoria.
 *
 * La referencia se almacena para aportar trazabilidad, facilitar investigaciones de soporte, realizar
 * conciliaciones y permitir futuras operaciones de captura o reembolso. Los invariantes del dominio
 * y de la base de datos solo la permiten en pagos `AUTHORIZED`.
 *
 * @property value referencia opaca de autorización del proveedor.
 * @throws IllegalArgumentException cuando [value] está en blanco.
 */
@JvmInline
value class PaymentProviderReference(val value: String) {
    /** Evita que los pagos satisfactorios contengan una referencia de proveedor inutilizable. */
    init {
        require(value.isNotBlank()) { "Payment provider reference must not be blank" }
    }
}

/**
 * Motivo legible por máquina para un rechazo de negocio conocido.
 *
 * Este tipo no se usa deliberadamente para tiempos de espera agotados, errores de conexión u otros
 * fallos técnicos; estos atraviesan el límite
 * [com.orderflow.payment.application.port.out.PaymentGatewayException]. Mantener separados ambos
 * conceptos permite que un futuro consumidor de Kafka reintente los fallos técnicos sin tratarlos
 * como rechazos de tarjeta.
 *
 * @property value código de motivo estable proporcionado o asignado por el adaptador de la pasarela.
 * @throws IllegalArgumentException cuando [value] está en blanco.
 */
@JvmInline
value class PaymentFailureReason(val value: String) {
    /** Garantiza que cada pago rechazado explique su resultado de negocio. */
    init {
        require(value.isNotBlank()) { "Payment failure reason must not be blank" }
    }
}

/**
 * Valor monetario inmutable que utiliza el dominio de Pagos.
 *
 * [BigDecimal] es obligatorio para la aritmética monetaria decimal, pues los tipos binarios de coma
 * flotante introducirían errores de redondeo. La construcción es privada para que todos los valores
 * pasen por [euros] u [of], que normalizan el importe a exactamente dos decimales. La primera versión
 * del servicio solo acepta EUR, pero la moneda se mantiene explícita para evitar importes ambiguos y
 * facilitar ampliaciones futuras.
 *
 * @property amount importe decimal no negativo normalizado a dos dígitos fraccionarios.
 * @property currency moneda ISO-4217; actualmente debe ser EUR.
 * @throws IllegalArgumentException ante un importe negativo, una moneda no admitida o una escala no válida.
 * @throws ArithmeticException cuando la normalización requeriría redondear en lugar de añadir ceros.
 */
@ConsistentCopyVisibility
data class Money private constructor(
    val amount: BigDecimal,
    val currency: Currency,
) {
    /** Vuelve a validar todos los invariantes de cada instancia, incluidas las copias generadas de la clase de datos. */
    init {
        // `signum` compara numéricamente y no se ve afectado por la escala de BigDecimal.
        require(amount.signum() >= 0) { "Payment amount cannot be negative" }
        require(currency == EUR) { "Only EUR is currently supported" }
        require(amount.scale() == SCALE) { "Payment amount must use two decimal places" }
    }

    /** Política controlada de construcción y normalización de valores monetarios. */
    companion object {
        /** Número de decimales que usan EUR y la columna `NUMERIC(19, 2)` de PostgreSQL. */
        private const val SCALE = 2

        /** Instancia canónica de moneda de Java que usan todos los pagos admitidos actualmente. */
        val EUR: Currency = Currency.getInstance("EUR")

        /**
         * Crea un importe en EUR y normaliza valores como `10` o `10.0` a `10.00`.
         *
         * @param amount importe decimal que debe ser no negativo y representable exactamente con escala 2.
         * @return valor monetario en EUR validado.
         */
        fun euros(amount: BigDecimal): Money = Money(normalize(amount), EUR)

        /**
         * Reconstruye el valor monetario cuando la moneda se proporciona explícitamente, principalmente desde persistencia.
         *
         * @param amount importe decimal almacenado o proporcionado externamente.
         * @param currency moneda ISO-4217 explícita que se debe validar.
         * @return valor monetario normalizado y validado.
         */
        fun of(amount: BigDecimal, currency: Currency): Money = Money(normalize(amount), currency)

        /**
         * Aplica la escala común sin cambiar silenciosamente el valor monetario.
         *
         * [RoundingMode.UNNECESSARY] acepta `10`, `10.0` y `10.00`, pero rechaza `10.001` en lugar
         * de redondear un importe de negocio a espaldas del llamador.
         */
        private fun normalize(amount: BigDecimal): BigDecimal =
            amount.setScale(SCALE, RoundingMode.UNNECESSARY)
    }
}

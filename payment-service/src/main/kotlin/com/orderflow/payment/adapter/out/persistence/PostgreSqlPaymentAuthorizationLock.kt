package com.orderflow.payment.adapter.`out`.persistence

import com.orderflow.payment.application.port.`out`.PaymentAuthorizationLock
import com.orderflow.payment.domain.model.OrderId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Implementación PostgreSQL del puerto de salida [PaymentAuthorizationLock], circunscrito al pedido.
 *
 * Los bloqueos asesores de PostgreSQL en el nivel de transacción proporcionan exclusión mutua entre
 * hilos e instancias del servicio sin añadir una tabla técnica de bloqueos. El identificador externo
 * del pedido se resume en la clave con signo de 64 bits que espera PostgreSQL. Por tanto, los
 * identificadores de pedido iguales compiten por un bloqueo, mientras que los pedidos no relacionados
 * suelen avanzar en paralelo. Una colisión teórica del resumen solo causa serialización adicional;
 * no puede debilitar la corrección ni mezclar datos de pagos.
 *
 * [TransactionTemplate] es esencial porque `pg_advisory_xact_lock` solo se libera automáticamente
 * cuando su transacción de base de datos se confirma o se revierte. Las llamadas al repositorio
 * realizadas dentro de `operation` se unen a la misma transacción de Spring, lo que convierte la
 * secuencia de lectura, creación, pasarela y escritura en una unidad serializada.
 *
 * @property jdbcTemplate ejecuta la pequeña sentencia de bloqueo específica del proveedor.
 * @param transactionManager gestor de transacciones de Spring usado para crear la plantilla reutilizable.
 */
@Component
class PostgreSqlPaymentAuthorizationLock(
    private val jdbcTemplate: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) : PaymentAuthorizationLock {
    /** Límite programático de transacción que engloba la adquisición, el trabajo, la confirmación y la liberación del bloqueo. */
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * Ejecuta un flujo de autorización mientras PostgreSQL posee el bloqueo asesor específico del pedido.
     *
     * `pg_advisory_xact_lock` espera cuando otra transacción procesa actualmente la misma clave. La
     * sentencia solo regresa cuando esta transacción posee el bloqueo. [requireNotNull] protege el
     * contrato genérico no nulo porque la API de transacciones de Java de Spring expresa los resultados
     * de la función como anulables aunque [operation] esté restringida a `T : Any`.
     *
     * Si [operation] lanza una excepción, [TransactionTemplate] revierte y PostgreSQL libera el
     * bloqueo. El servicio de autorización captura deliberadamente los fallos técnicos de la pasarela
     * dentro de la función para confirmar su fila pendiente antes de volver a lanzar la excepción fuera
     * de este método.
     *
     * @param orderId clave de negocio usada para derivar la clave del bloqueo asesor.
     * @param operation flujo completo de autorización que se debe serializar.
     * @return resultado no nulo de la operación tras confirmar satisfactoriamente la transacción.
     */
    override fun <T : Any> withLock(orderId: OrderId, operation: () -> T): T =
        requireNotNull(
            transactionTemplate.execute {
                // `hashtextextended` convierte de forma determinista la cadena externa en una clave BIGINT.
                jdbcTemplate.queryForObject(
                    "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                    String::class.java,
                    orderId.value,
                )
                // Las siguientes operaciones del repositorio participan en esta misma transacción y en el bloqueo mantenido.
                operation()
            },
        )
}

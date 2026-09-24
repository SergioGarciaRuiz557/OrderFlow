package com.orderflow.payment.adapter.`out`.persistence

import com.orderflow.payment.application.port.`out`.DuplicatePaymentException
import com.orderflow.payment.application.port.`out`.PaymentRepository
import com.orderflow.payment.domain.model.OrderId
import com.orderflow.payment.domain.model.Payment
import com.orderflow.payment.domain.model.PaymentId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Implementación PostgreSQL/JPA del puerto de salida [PaymentRepository], independiente del framework.
 *
 * El adaptador posee las declaraciones de transacciones de persistencia y toda la conversión entre
 * agregados de dominio y entidades JPA. Los llamadores de la aplicación solo ven [Payment],
 * [PaymentId] y [OrderId]. Se usa `saveAndFlush` deliberadamente para que los fallos de bloqueo
 * optimista y de restricciones de la base de datos ocurran dentro de este adaptador, donde las
 * excepciones de infraestructura pueden traducirse antes de atravesar el límite hexagonal.
 *
 * Cuando se llaman desde [PostgreSqlPaymentAuthorizationLock], estos métodos se unen a su transacción
 * existente. Cuando se llaman de forma independiente, cada anotación crea el ámbito apropiado de solo
 * lectura o de lectura y escritura.
 *
 * @property repository repositorio interno de entidades de Spring Data.
 * @property mapper traductor explícito entre las representaciones de persistencia y dominio.
 */
@Repository
class JpaPaymentRepositoryAdapter(
    private val repository: SpringDataPaymentRepository,
    private val mapper: PaymentPersistenceMapper,
) : PaymentRepository {

    /**
     * Carga un pago por su clave primaria en una transacción de solo lectura.
     *
     * `Optional.orElse(null)` convierte la representación de Spring Data en la convención anulable
     * del puerto central; la asignación solo se ejecuta cuando existe una entidad.
     *
     * @param paymentId identificador de dominio con tipado fuerte que se desenvuelve para la consulta JPA.
     * @return agregado reconstruido o `null` cuando no existe ninguna fila.
     */
    @Transactional(readOnly = true)
    override fun findById(paymentId: PaymentId): Payment? =
        repository.findById(paymentId.value).orElse(null)?.let(mapper::toDomain)

    /**
     * Carga el único pago asociado a la operación de negocio de un pedido.
     *
     * @param orderId clave de idempotencia de negocio que se desenvuelve para la consulta derivada de Spring Data.
     * @return agregado reconstruido o `null` antes de la primera solicitud de autorización del pedido.
     */
    @Transactional(readOnly = true)
    override fun findByOrderId(orderId: OrderId): Payment? =
        repository.findByOrderId(orderId.value)?.let(mapper::toDomain)

    /**
     * Inserta un pago nuevo o actualiza de forma optimista una instantánea de pago existente.
     *
     * El dominio se convierte en una entidad separada, `saveAndFlush` fuerza inmediatamente la
     * ejecución de SQL y la entidad resultante se vuelve a asignar para que los llamadores reciban la
     * versión asignada por la base de datos. Las restricciones únicas de la base de datos siguen siendo
     * autoritativas bajo concurrencia aunque se hayan eludido las comprobaciones de la aplicación.
     *
     * @param payment instantánea validada del agregado.
     * @return instantánea conservada que contiene la nueva versión de bloqueo optimista.
     * @throws DuplicatePaymentException cuando PostgreSQL rechaza un conflicto de unicidad o integridad.
     */
    @Transactional
    override fun save(payment: Payment): Payment = try {
        // Vaciar aquí mantiene los errores de restricciones dentro de este límite de traducción.
        mapper.toDomain(repository.saveAndFlush(mapper.toEntity(payment)))
    } catch (exception: DataIntegrityViolationException) {
        // Los llamadores centrales no necesitan saber qué excepción de Spring representó el fallo de la base de datos.
        throw DuplicatePaymentException(exception)
    }
}

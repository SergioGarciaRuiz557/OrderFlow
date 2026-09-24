package com.orderflow.inventory.adapter.`out`.persistence

import com.orderflow.inventory.application.port.`out`.ConcurrentInventoryModificationException
import com.orderflow.inventory.application.port.`out`.InventoryRepository
import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.OrderId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Implementación PostgreSQL/JPA del puerto [InventoryRepository] independiente del framework.
 *
 * El adaptador es responsable de las fronteras de transacción de las operaciones individuales de
 * persistencia, delega la ejecución de consultas en Spring Data y convierte los grafos de entidades
 * mediante [InventoryPersistenceMapper]. También traduce las excepciones del framework al vocabulario
 * de concurrencia de la aplicación para que las capas superiores no dependan de Spring Data.
 *
 * @property repository repositorio de Spring Data específico de la tecnología.
 * @property mapper traductor explícito entre las representaciones de persistencia y dominio.
 */
@Repository
class JpaInventoryRepositoryAdapter(
    private val repository: SpringDataInventoryRepository,
    private val mapper: InventoryPersistenceMapper,
) : InventoryRepository {

    /**
     * Carga un agregado de inventario completo por producto en una transacción de solo lectura.
     *
     * @param productId identificador de dominio convertido en la clave String de la base de datos.
     * @return agregado de dominio mapeado, o `null` cuando no existe ninguna fila.
     */
    @Transactional(readOnly = true)
    override fun findByProductId(productId: ProductId): InventoryItem? =
        repository.findAggregateByProductId(productId.value)?.let(mapper::toDomain)

    /**
     * Busca el agregado al que pertenece una reserva en una transacción de solo lectura.
     *
     * @param reservationId identificador de dominio convertido en la clave UUID de la base de datos.
     * @return agregado propietario completo, o `null` cuando la reserva es desconocida.
     */
    @Transactional(readOnly = true)
    override fun findByReservationId(reservationId: ReservationId): InventoryItem? =
        repository.findAggregateByReservationId(reservationId.value)?.let(mapper::toDomain)

    @Transactional(readOnly = true)
    override fun findByOrderId(orderId: OrderId): List<InventoryItem> =
        repository.findAggregatesByOrderId(orderId.value).map(mapper::toDomain)

    /**
     * Inserta o actualiza de forma optimista un agregado de inventario completo.
     *
     * `saveAndFlush` es importante porque fuerza que las infracciones de bloqueo optimista y unicidad
     * se produzcan dentro de este método, donde pueden traducirse de forma coherente. Una infracción
     * de unicidad puede representar una carrera entre inserciones de productos nuevos o reservas
     * duplicadas de producto/pedido, por lo que se trata como un conflicto de concurrencia y la
     * aplicación vuelve a cargar el estado actual.
     *
     * @param inventoryItem instantánea inmutable del agregado que se persistirá.
     * @return instantánea persistida del dominio que contiene la versión actualizada.
     * @throws ConcurrentInventoryModificationException cuando otra transacción prevalece en una
     * actualización o inserción incompatible.
     */
    @Transactional
    override fun save(inventoryItem: InventoryItem): InventoryItem = try {
        mapper.toDomain(repository.saveAndFlush(mapper.toEntity(inventoryItem)))
    } catch (exception: OptimisticLockingFailureException) {
        throw ConcurrentInventoryModificationException(exception)
    } catch (exception: DataIntegrityViolationException) {
        // Una inserción concurrente puede competir por la clave primaria del producto o la restricción única producto/pedido.
        throw ConcurrentInventoryModificationException(exception)
    }
}

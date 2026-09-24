package com.orderflow.inventory.application.service

import com.orderflow.inventory.application.port.`in`.CreateOrUpdateInventoryUseCase
import com.orderflow.inventory.application.port.`in`.GetInventoryUseCase
import com.orderflow.inventory.application.port.`in`.ReleaseInventoryUseCase
import com.orderflow.inventory.application.port.`in`.ReserveInventoryCommand
import com.orderflow.inventory.application.port.`in`.ReserveInventoryUseCase
import com.orderflow.inventory.application.port.`out`.ClockProvider
import com.orderflow.inventory.application.port.`out`.ConcurrentInventoryModificationException
import com.orderflow.inventory.application.port.`out`.InventoryRepository
import com.orderflow.inventory.domain.model.InventoryItem
import com.orderflow.inventory.domain.model.ProductId
import com.orderflow.inventory.domain.model.ReleaseResult
import com.orderflow.inventory.domain.model.ReservationId
import com.orderflow.inventory.domain.model.ReservationRejectionReason
import com.orderflow.inventory.domain.model.ReservationResult
import org.springframework.stereotype.Service

/**
 * Servicio de aplicación que coordina todos los casos de uso de Inventory.
 *
 * Esta clase es deliberadamente ligera: los repositorios localizan y persisten agregados,
 * [ClockProvider] proporciona marcas temporales deterministas e [InventoryItem] es responsable de
 * la aritmética de existencias y las reglas de negocio. El servicio también ofrece una política de
 * reintentos acotada para los conflictos de concurrencia optimista. Cada reintento vuelve a cargar
 * el estado actual e invoca de nuevo el comportamiento del dominio, de modo que la respuesta final
 * refleja las últimas existencias en lugar de repetir a ciegas una escritura obsoleta.
 *
 * @property inventoryRepository acceso independiente del framework a los agregados de inventario persistidos.
 * @property clockProvider fuente de marcas temporales de reserva y liberación.
 */
@Service
class InventoryApplicationService(
    private val inventoryRepository: InventoryRepository,
    private val clockProvider: ClockProvider,
) : ReserveInventoryUseCase,
    ReleaseInventoryUseCase,
    GetInventoryUseCase,
    CreateOrUpdateInventoryUseCase {

    /**
     * Carga el inventario, delega las reglas de reserva en el agregado y persiste los cambios aceptados.
     *
     * La ausencia de un producto es un rechazo de negocio. Las solicitudes activas duplicadas exactas
     * devuelven su reserva existente sin volver a escribir. Las nuevas reservas aceptadas se guardan,
     * mientras que todos los resultados rechazados se devuelven sin cambios. El `when` sellado
     * mantiene exhaustivo el procesamiento de resultados.
     *
     * @param command producto, pedido y cantidad que se reservarán.
     * @return resultado de dominio aceptado o rechazado según el último estado disponible del agregado.
     */
    override fun reserve(command: ReserveInventoryCommand): ReservationResult = retryOnConflict {
        val inventoryItem = inventoryRepository.findByProductId(command.productId)
            ?: return@retryOnConflict ReservationResult.Rejected(
                reason = ReservationRejectionReason.INVENTORY_ITEM_NOT_FOUND,
                availableQuantity = null,
            )

        when (val result = inventoryItem.reserve(
            reservationId = ReservationId.new(),
            orderId = command.orderId,
            quantity = command.quantity,
            at = clockProvider.now(),
        )) {
            // Un rechazo de negocio no modifica el estado y, por tanto, no requiere una llamada de persistencia.
            is ReservationResult.Rejected -> result
            is ReservationResult.Reserved -> {
                if (result.wasAlreadyReserved) {
                    // Un duplicado exacto ya se ha persistido; devolverlo es la ruta idempotente.
                    result
                } else {
                    // Guardar el agregado completo persiste atómicamente las existencias y el historial de reservas.
                    val saved = inventoryRepository.save(result.inventoryItem)
                    result.copy(inventoryItem = saved)
                }
            }
        }
    }

    /**
     * Localiza y libera una reserva, y persiste la reposición de existencias solo en su primera liberación.
     *
     * Tanto las reservas desconocidas como las ya liberadas siguen siendo visibles para el consumidor.
     * Solo una transición [ReleaseResult.Released] real produce una escritura en la base de datos.
     *
     * @param reservationId reserva cuya compensación se solicita.
     * @return resultado exhaustivo de la liberación evaluado respecto al estado persistido actual.
     */
    override fun release(reservationId: ReservationId): ReleaseResult = retryOnConflict {
        val inventoryItem = inventoryRepository.findByReservationId(reservationId)
            ?: return@retryOnConflict ReleaseResult.ReservationNotFound(reservationId)

        when (val result = inventoryItem.release(reservationId, clockProvider.now())) {
            // Estos dos resultados conservan el estado, por lo que ninguno debe incrementar la versión JPA.
            is ReleaseResult.ReservationNotFound -> result
            is ReleaseResult.AlreadyReleased -> result
            is ReleaseResult.Released -> {
                val saved = inventoryRepository.save(result.inventoryItem)
                result.copy(inventoryItem = saved)
            }
        }
    }

    /**
     * Devuelve la instantánea actual del agregado para su consulta administrativa.
     *
     * @param productId producto consultado.
     * @return inventario persistido, o `null` si no existe.
     */
    override fun get(productId: ProductId): InventoryItem? = inventoryRepository.findByProductId(productId)

    /**
     * Crea el inventario o sustituye sus existencias disponibles actuales.
     *
     * La validación se realiza antes de acceder al repositorio para que los valores negativos no
     * válidos fallen de inmediato. Los agregados existentes conservan sus reservas; los productos
     * ausentes comienzan con un historial de reservas vacío. Los conflictos de concurrencia hacen
     * que el agregado actual vuelva a cargarse antes de aplicar la cantidad.
     *
     * @param productId producto que se inicializa o ajusta.
     * @param quantity nueva cantidad disponible; cero es válido.
     * @return agregado guardado con su versión actual de persistencia.
     * @throws IllegalArgumentException cuando [quantity] es negativo.
     */
    override fun setAvailableQuantity(productId: ProductId, quantity: Int): InventoryItem {
        require(quantity >= 0) { "Available stock cannot be negative" }
        return retryOnConflict {
            val inventoryItem = inventoryRepository.findByProductId(productId)
                ?.setAvailableQuantity(quantity)
                ?: InventoryItem.create(productId, quantity)
            inventoryRepository.save(inventoryItem)
        }
    }

    /**
     * Ejecuta [operation] con una pequeña política acotada de reintentos por concurrencia optimista.
     *
     * Cada reintento vuelve a ejecutar la lambda completa, incluidas las lecturas del repositorio y
     * la evaluación del dominio. Esto es esencial para la corrección: después de que otra reserva
     * consuma la última unidad, un reintento debe observar ese estado y devolver existencias
     * insuficientes en vez de forzar la asignación obsoleta. Se permite que el último intento propague
     * su excepción, lo que evita un bucle ilimitado durante una contención sostenida o ante un fallo
     * de infraestructura clasificado incorrectamente.
     *
     * @param operation unidad completa de lectura, evaluación y escritura que se reintentará.
     * @return el primer resultado satisfactorio de la operación.
     * @throws ConcurrentInventoryModificationException si todos los intentos entran en conflicto.
     */
    private fun <T> retryOnConflict(operation: () -> T): T {
        repeat(MAX_CONCURRENCY_ATTEMPTS - 1) {
            try {
                return operation()
            } catch (_: ConcurrentInventoryModificationException) {
                // Vuelve a cargar el agregado y a evaluar sus invariantes.
            }
        }
        return operation()
    }

    /** Constantes de la política interna de concurrencia. */
    private companion object {
        /** Número máximo de intentos completos antes de propagar el conflicto final. */
        const val MAX_CONCURRENCY_ATTEMPTS = 3
    }
}

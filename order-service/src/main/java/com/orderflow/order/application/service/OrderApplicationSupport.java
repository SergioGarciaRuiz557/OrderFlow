package com.orderflow.order.application.service;

import com.orderflow.order.application.exception.OrderNotFoundException;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;

/**
 * Operaciones compartidas de orquestación con visibilidad de paquete que utilizan los servicios de comandos.
 *
 * <p>No es un puerto de aplicación porque se trata de un detalle de implementación, no de una capacidad
 * expuesta a través de un límite arquitectónico.</p>
 */
final class OrderApplicationSupport {
    /** Impide crear instancias de esta clase auxiliar sin estado. */
    private OrderApplicationSupport() {
    }

    /**
     * Carga un agregado y convierte un resultado vacío del repositorio en la excepción del nivel de aplicación.
     *
     * @param repository límite de persistencia
     * @param id identidad solicitada del agregado
     * @return agregado cargado
     * @throws OrderNotFoundException cuando el repositorio no contiene un agregado coincidente
     */
    static Order load(OrderRepository repository, OrderId id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Persiste el estado antes de entregar los eventos recién producidos al publicador de salida.
     *
     * <p>Llamar a {@link Order#pullDomainEvents()} después del guardado garantiza que no se publiquen eventos de una
     * operación de persistencia fallida. La transacción del servicio que la envuelve puede revertirse si falla
     * el propio publicador.</p>
     *
     * @param repository límite de persistencia del agregado
     * @param publisher límite de entrega de eventos independiente de la tecnología
     * @param order agregado modificado
     */
    static void saveAndPublish(OrderRepository repository, IntegrationMessagePublisher publisher, Order order) {
        repository.save(order);
        publisher.publish(order.pullDomainEvents());
    }
}

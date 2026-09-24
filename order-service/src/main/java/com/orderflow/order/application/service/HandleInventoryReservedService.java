package com.orderflow.order.application.service;

import com.orderflow.order.application.port.in.HandleInventoryReservedUseCase;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Gestiona una notificación correcta del inventario e inicia el siguiente paso de la saga.
 *
 * <p>El agregado registra primero que el inventario está reservado. Solo entonces puede aceptar la solicitud de
 * autorización del pago, lo que protege el orden requerido del ciclo de vida.</p>
 */
@Service
public class HandleInventoryReservedService implements HandleInventoryReservedUseCase {
    /** Puerto de persistencia del agregado. */
    private final OrderRepository repository;
    /** Puerto de publicación de eventos de dominio. */
    private final IntegrationMessagePublisher publisher;
    /** Proveedor del tiempo de negocio. */
    private final ClockProvider clock;

    /**
     * Crea el servicio de notificación de inventario correcto.
     *
     * @param repository puerto de persistencia del agregado
     * @param publisher puerto de publicación de eventos de dominio
     * @param clock puerto del tiempo de negocio
     */
    public HandleInventoryReservedService(OrderRepository repository, IntegrationMessagePublisher publisher, ClockProvider clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Hace avanzar el pedido referenciado desde inventario reservado hasta pago pendiente y persiste ambos
     * hechos de dominio de forma atómica.
     *
     * @param orderId pedido al que hace referencia el resultado del inventario
     */
    @Override
    @Transactional
    public void handle(UUID orderId) {
        Order order = OrderApplicationSupport.load(repository, new OrderId(orderId));
        // Ambas transiciones consecutivas pertenecen a una sola notificación y comparten su instante de procesamiento.
        Instant now = clock.now();
        order.markInventoryReserved(now);
        order.requestPaymentAuthorization(now);
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
    }
}

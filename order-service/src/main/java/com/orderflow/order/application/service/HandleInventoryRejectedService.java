package com.orderflow.order.application.service;

import com.orderflow.order.application.port.in.HandleInventoryRejectedUseCase;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Gestiona una reserva de inventario fallida cancelando el pedido referenciado. */
@Service
public class HandleInventoryRejectedService implements HandleInventoryRejectedUseCase {
    /** Puerto de persistencia del agregado. */
    private final OrderRepository repository;
    /** Puerto de publicación de eventos de dominio. */
    private final IntegrationMessagePublisher publisher;
    /** Proveedor del tiempo de negocio. */
    private final ClockProvider clock;

    /**
     * Crea el servicio de notificación de inventario rechazado.
     *
     * @param repository puerto de persistencia del agregado
     * @param publisher puerto de publicación de eventos de dominio
     * @param clock puerto del tiempo de negocio
     */
    public HandleInventoryRejectedService(OrderRepository repository, IntegrationMessagePublisher publisher, ClockProvider clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Carga, rechaza, guarda y publica los hechos de cancelación en una sola transacción.
     *
     * @param orderId pedido al que hace referencia el resultado del inventario
     * @param reason explicación del rechazo del inventario
     */
    @Override
    @Transactional
    public void handle(UUID orderId, String reason) {
        Order order = OrderApplicationSupport.load(repository, new OrderId(orderId));
        order.markInventoryRejected(reason, clock.now());
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
    }
}

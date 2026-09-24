package com.orderflow.order.application.service;

import com.orderflow.order.application.port.in.HandlePaymentRejectedUseCase;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Gestiona una autorización de pago rechazada e inicia la compensación del inventario. */
@Service
public class HandlePaymentRejectedService implements HandlePaymentRejectedUseCase {
    /** Puerto de persistencia del agregado. */
    private final OrderRepository repository;
    /** Puerto de publicación de eventos de dominio. */
    private final IntegrationMessagePublisher publisher;
    /** Proveedor del tiempo de negocio. */
    private final ClockProvider clock;

    /**
     * Crea el servicio de notificación de pago rechazado.
     *
     * @param repository puerto de persistencia del agregado
     * @param publisher puerto de publicación de eventos de dominio
     * @param clock puerto del tiempo de negocio
     */
    public HandlePaymentRejectedService(OrderRepository repository, IntegrationMessagePublisher publisher, ClockProvider clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Carga el pedido, registra el rechazo y la intención de liberación, y después persiste y publica.
     *
     * @param orderId pedido al que hace referencia el resultado del pago
     * @param reason explicación del rechazo del pago
     */
    @Override
    @Transactional
    public void handle(UUID orderId, String reason) {
        Order order = OrderApplicationSupport.load(repository, new OrderId(orderId));
        order.markPaymentRejected(reason, clock.now());
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
    }
}

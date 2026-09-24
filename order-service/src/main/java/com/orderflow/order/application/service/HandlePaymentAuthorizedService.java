package com.orderflow.order.application.service;

import com.orderflow.order.application.port.in.HandlePaymentAuthorizedUseCase;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Gestiona una autorización de pago correcta confirmando el pedido referenciado. */
@Service
public class HandlePaymentAuthorizedService implements HandlePaymentAuthorizedUseCase {
    /** Puerto de persistencia del agregado. */
    private final OrderRepository repository;
    /** Puerto de publicación de eventos de dominio. */
    private final IntegrationMessagePublisher publisher;
    /** Proveedor del tiempo de negocio. */
    private final ClockProvider clock;

    /**
     * Crea el servicio de notificación de pago correcto.
     *
     * @param repository puerto de persistencia del agregado
     * @param publisher puerto de publicación de eventos de dominio
     * @param clock puerto del tiempo de negocio
     */
    public HandlePaymentAuthorizedService(OrderRepository repository, IntegrationMessagePublisher publisher, ClockProvider clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Carga el pedido, delega las reglas de confirmación en el agregado, persiste y publica.
     *
     * @param orderId pedido al que hace referencia el resultado del pago
     */
    @Override
    @Transactional
    public void handle(UUID orderId) {
        Order order = OrderApplicationSupport.load(repository, new OrderId(orderId));
        order.markPaymentAuthorized(clock.now());
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
    }
}

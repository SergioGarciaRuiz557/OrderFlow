package com.orderflow.order.application.service;

import com.orderflow.order.application.model.OrderView;
import com.orderflow.order.application.port.in.CreateOrderUseCase;
import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.IntegrationMessagePublisher;
import com.orderflow.order.application.port.out.OrderIdGenerator;
import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.CustomerId;
import com.orderflow.order.domain.model.Money;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderLine;
import com.orderflow.order.domain.model.PaymentMethodId;
import com.orderflow.order.domain.model.ProductId;
import com.orderflow.order.domain.model.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implementación transaccional de {@link CreateOrderUseCase}.
 *
 * <p>El servicio mapea los valores primitivos del comando a objetos de dominio validados, crea el agregado, le pide
 * que inicie la reserva del inventario, lo persiste y publica sus hechos de dominio. Las reglas de valoración y
 * transición de estado permanecen dentro de {@link Order}.</p>
 */
@Service
public class CreateOrderService implements CreateOrderUseCase {
    /** Almacena el agregado mediante un puerto de salida hexagonal. */
    private final OrderRepository repository;
    /** Entrega los hechos de dominio recién producidos sin exponer un transporte. */
    private final IntegrationMessagePublisher publisher;
    /** Proporciona un instante determinista para la creación y la solicitud inicial. */
    private final ClockProvider clock;
    /** Proporciona una identidad nueva de Order. */
    private final OrderIdGenerator idGenerator;

    /**
     * Crea el servicio con todas las dependencias no deterministas y de infraestructura inyectadas.
     *
     * @param repository puerto de persistencia del agregado
     * @param publisher puerto de publicación de eventos de dominio
     * @param clock puerto del tiempo de negocio
     * @param idGenerator puerto de generación de identidades
     */
    public CreateOrderService(OrderRepository repository, IntegrationMessagePublisher publisher,
                              ClockProvider clock, OrderIdGenerator idGenerator) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * Crea y persiste un pedido en estado de reserva de inventario pendiente.
     *
     * @param command datos del cliente, los artículos y el pago
     * @return representación del pedido creado en la aplicación
     */
    @Override
    @Transactional
    public OrderView create(CreateOrderCommand command) {
        // La conversión invoca los constructores de los objetos de valor, por lo que la validación del dominio no depende de HTTP.
        List<OrderLine> lines = command.items().stream()
                .map(item -> new OrderLine(new ProductId(item.productId()), new Quantity(item.quantity()),
                        Money.eur(item.unitPrice())))
                .toList();
        // Un solo instante proporciona una marca temporal coherente a la creación y a su solicitud inmediata de inventario.
        Instant now = clock.now();
        Order order = Order.create(idGenerator.nextId(), new CustomerId(command.customerId()), lines,
                new PaymentMethodId(command.paymentMethodId()), now);
        order.requestInventoryReservation(now);
        OrderApplicationSupport.saveAndPublish(repository, publisher, order);
        return OrderView.from(order);
    }
}

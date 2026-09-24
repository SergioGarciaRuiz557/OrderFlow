package com.orderflow.order.adapter.out.persistence;

import com.orderflow.order.domain.model.CustomerId;
import com.orderflow.order.domain.model.Money;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import com.orderflow.order.domain.model.OrderLine;
import com.orderflow.order.domain.model.PaymentMethodId;
import com.orderflow.order.domain.model.ProductId;
import com.orderflow.order.domain.model.Quantity;
import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.List;

/**
 * Mapeador bidireccional explícito entre el agregado rico y los registros JPA mutables.
 *
 * <p>El mapeo es código visible de forma intencionada, en lugar de magia basada en reflexión: aquí se puede auditar
 * cada campo persistido para recuperar la saga y los constructores del dominio vuelven a validar los valores de la base de datos.</p>
 */
@Component
class OrderPersistenceMapper {
    /**
     * Aplana un agregado en una fila de pedido y sus filas de línea adjuntas.
     *
     * @param order agregado de origen
     * @return grafo de persistencia separado y nuevo, listo para que Spring Data lo guarde o fusione
     */
    JpaOrderEntity toEntity(Order order) {
        JpaOrderEntity entity = new JpaOrderEntity(order.id().value(), order.customerId().value(), order.status(),
                order.paymentMethodId().value(), order.total().amount(), order.total().currency().getCurrencyCode(),
                order.createdAt(), order.updatedAt(), order.version());
        order.lines().stream()
                .map(line -> new JpaOrderLineEntity(line.productId().value(), line.quantity().value(),
                        line.unitPrice().amount(), line.unitPrice().currency().getCurrencyCode()))
                .forEach(entity::addLine);
        return entity;
    }

    /**
     * Reconstruye y valida un agregado del dominio a partir de un grafo de persistencia completo.
     *
     * <p>{@link Order#rehydrate} verifica el total persistido y no crea eventos históricos.</p>
     *
     * @param entity entidad de pedido cargada con las filas de línea obtenidas de forma inmediata
     * @return agregado independiente del framework
     */
    Order toDomain(JpaOrderEntity entity) {
        List<OrderLine> lines = entity.lines().stream()
                .map(line -> new OrderLine(new ProductId(line.productId()), new Quantity(line.quantity()),
                        new Money(line.unitPrice(), Currency.getInstance(line.currency()))))
                .toList();
        return Order.rehydrate(new OrderId(entity.id()), new CustomerId(entity.customerId()), lines,
                new PaymentMethodId(entity.paymentMethodId()), entity.status(),
                new Money(entity.total(), Currency.getInstance(entity.currency())),
                entity.createdAt(), entity.updatedAt(), entity.version());
    }
}

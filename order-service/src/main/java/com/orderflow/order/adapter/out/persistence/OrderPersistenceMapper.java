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
 * Explicit bidirectional mapper between the rich aggregate and mutable JPA records.
 *
 * <p>Mapping is intentionally visible code rather than reflection-based magic: every persisted saga
 * recovery field can be audited here, and domain constructors revalidate database values.</p>
 */
@Component
class OrderPersistenceMapper {
    /**
     * Flattens an aggregate into an order row and attached line rows.
     *
     * @param order source aggregate
     * @return new detached persistence graph ready for Spring Data save/merge
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
     * Reconstructs and validates a domain aggregate from a complete persistence graph.
     *
     * <p>{@link Order#rehydrate} verifies the persisted total and creates no historical events.</p>
     *
     * @param entity loaded order entity with eager line rows
     * @return framework-independent aggregate
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

package com.orderflow.order.adapter.out.persistence;

import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Implements the domain-facing repository port with Spring Data JPA and explicit mapping. */
@Repository
public class JpaOrderRepositoryAdapter implements OrderRepository {
    /** Internal CRUD mechanism that operates only on persistence entities. */
    private final SpringDataOrderRepository repository;
    /** Translation boundary between persistence and domain representations. */
    private final OrderPersistenceMapper mapper;

    /**
     * Creates the repository adapter.
     *
     * @param repository internal Spring Data repository
     * @param mapper explicit persistence mapper
     */
    public JpaOrderRepositoryAdapter(SpringDataOrderRepository repository, OrderPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Maps and saves a complete aggregate, then maps the managed result back to include its version.
     *
     * @param order aggregate to insert or update
     * @return saved aggregate representation
     */
    @Override
    public Order save(Order order) {
        return mapper.toDomain(repository.save(mapper.toEntity(order)));
    }

    /**
     * Loads and rehydrates an aggregate when its UUID exists.
     *
     * @param orderId requested identity
     * @return optional domain aggregate
     */
    @Override
    public Optional<Order> findById(OrderId orderId) {
        return repository.findById(orderId.value()).map(mapper::toDomain);
    }
}

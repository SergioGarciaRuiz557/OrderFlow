package com.orderflow.order.adapter.out.persistence;

import com.orderflow.order.application.port.out.OrderRepository;
import com.orderflow.order.domain.model.Order;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Implementa el puerto de repositorio orientado al dominio con Spring Data JPA y mapeo explícito. */
@Repository
public class JpaOrderRepositoryAdapter implements OrderRepository {
    /** Mecanismo CRUD interno que opera únicamente con entidades de persistencia. */
    private final SpringDataOrderRepository repository;
    /** Límite de traducción entre las representaciones de persistencia y del dominio. */
    private final OrderPersistenceMapper mapper;

    /**
     * Crea el adaptador de repositorio.
     *
     * @param repository repositorio interno de Spring Data
     * @param mapper mapeador explícito de persistencia
     */
    public JpaOrderRepositoryAdapter(SpringDataOrderRepository repository, OrderPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Mapea y guarda un agregado completo y, a continuación, vuelve a mapear el resultado administrado para incluir su versión.
     *
     * @param order agregado que se insertará o actualizará
     * @return representación del agregado guardado
     */
    @Override
    public Order save(Order order) {
        return mapper.toDomain(repository.save(mapper.toEntity(order)));
    }

    /**
     * Carga y rehidrata un agregado cuando existe su UUID.
     *
     * @param orderId identidad solicitada
     * @return agregado opcional del dominio
     */
    @Override
    public Optional<Order> findById(OrderId orderId) {
        return repository.findById(orderId.value()).map(mapper::toDomain);
    }
}

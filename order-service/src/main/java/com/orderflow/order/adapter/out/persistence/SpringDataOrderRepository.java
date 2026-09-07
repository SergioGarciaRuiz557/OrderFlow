package com.orderflow.order.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Internal Spring Data repository for persistence entities.
 *
 * <p>Package-private visibility prevents adapters and application services from bypassing
 * {@link JpaOrderRepositoryAdapter} and leaking Spring Data outside the persistence adapter.</p>
 */
interface SpringDataOrderRepository extends JpaRepository<JpaOrderEntity, UUID> {
}

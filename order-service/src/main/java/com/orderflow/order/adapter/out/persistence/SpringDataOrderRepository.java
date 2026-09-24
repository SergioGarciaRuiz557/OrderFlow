package com.orderflow.order.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repositorio interno de Spring Data para entidades de persistencia.
 *
 * <p>La visibilidad de paquete impide que los adaptadores y los servicios de aplicación eludan
 * {@link JpaOrderRepositoryAdapter} y filtren Spring Data fuera del adaptador de persistencia.</p>
 */
interface SpringDataOrderRepository extends JpaRepository<JpaOrderEntity, UUID> {
}

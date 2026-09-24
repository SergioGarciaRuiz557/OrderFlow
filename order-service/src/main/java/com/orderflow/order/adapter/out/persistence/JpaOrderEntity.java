package com.orderflow.order.adapter.out.persistence;

import com.orderflow.order.domain.model.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representación JPA mutable de la tabla {@code orders}.
 *
 * <p>Deliberadamente, no es el agregado del dominio. Su forma y mutabilidad satisfacen las necesidades de
 * persistencia, mientras que {@link OrderPersistenceMapper} protege la aplicación de los tipos JPA.</p>
 */
@Entity
@Table(name = "orders")
public class JpaOrderEntity {
    /** Clave primaria UUID asignada por el dominio. */
    @Id
    private UUID id;

    /** Referencia del cliente almacenada como UUID escalar. */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Nombre persistido del enum; las transiciones permanecen bajo el control del agregado del dominio. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrderStatus status;

    /** Referencia de pago opaca necesaria para reanudar el flujo de trabajo después de un reinicio. */
    @Column(name = "payment_method_id", nullable = false)
    private String paymentMethodId;

    /** Total desnormalizado del agregado, verificado con las líneas durante la rehidratación. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    /** Código ISO de la divisa que acompaña al total. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Instante inmutable de creación del agregado. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Instante de la transición aceptada más reciente del ciclo de vida. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Versión que Hibernate incrementa para rechazar escrituras concurrentes obsoletas. */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Filas hijas que pertenecen a este registro de persistencia del agregado.
     * La carga inmediata permite que el adaptador reconstruya un agregado completo en su límite.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<JpaOrderLineEntity> lines = new ArrayList<>();

    /** Requerido por JPA; el código de producción crea entidades mediante el mapeador de persistencia. */
    protected JpaOrderEntity() {
    }

    /** Crea un registro de persistencia a partir del estado explícito del agregado. */
    JpaOrderEntity(UUID id, UUID customerId, OrderStatus status, String paymentMethodId,
                   BigDecimal total, String currency, Instant createdAt, Instant updatedAt, Long version) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.paymentMethodId = paymentMethodId;
        this.total = total;
        this.currency = currency;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * Mantiene ambos lados de la asociación JPA antes de propagar la persistencia en cascada.
     *
     * @param line entidad hija de persistencia que se adjuntará
     */
    void addLine(JpaOrderLineEntity line) {
        lines.add(line);
        line.attachTo(this);
    }

    /** Devuelve la clave primaria. @return UUID almacenado del agregado */
    UUID id() { return id; }
    /** Devuelve la columna del cliente. @return UUID almacenado del cliente */
    UUID customerId() { return customerId; }
    /** Devuelve la columna de estado. @return estado almacenado del ciclo de vida */
    OrderStatus status() { return status; }
    /** Devuelve la columna de referencia de pago. @return referencia de pago almacenada */
    String paymentMethodId() { return paymentMethodId; }
    /** Devuelve la columna del total. @return total almacenado del agregado */
    BigDecimal total() { return total; }
    /** Devuelve la columna de divisa. @return divisa almacenada del total */
    String currency() { return currency; }
    /** Devuelve la columna de creación. @return instante de creación almacenado */
    Instant createdAt() { return createdAt; }
    /** Devuelve la columna de última actualización. @return instante almacenado de la última actualización */
    Instant updatedAt() { return updatedAt; }
    /** Devuelve la columna de concurrencia. @return versión actual de bloqueo optimista */
    Long version() { return version; }
    /** Devuelve las entidades hijas adjuntas sin exponer almacenamiento mutable. @return vista inmutable de las líneas */
    List<JpaOrderLineEntity> lines() { return List.copyOf(lines); }
}

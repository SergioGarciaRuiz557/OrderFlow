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
 * Mutable JPA representation of the {@code orders} table.
 *
 * <p>This is deliberately not the domain aggregate. Its shape and mutability satisfy persistence
 * concerns, while {@link OrderPersistenceMapper} protects the application from JPA types.</p>
 */
@Entity
@Table(name = "orders")
public class JpaOrderEntity {
    /** Domain-assigned UUID primary key. */
    @Id
    private UUID id;

    /** Customer reference stored as a scalar UUID. */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** Persisted enum name; transitions remain controlled by the domain aggregate. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OrderStatus status;

    /** Opaque payment reference required to resume the workflow after restart. */
    @Column(name = "payment_method_id", nullable = false)
    private String paymentMethodId;

    /** Denormalized aggregate total, verified against lines during rehydration. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    /** ISO currency code accompanying the total. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Immutable aggregate creation time. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Time of the most recent accepted lifecycle transition. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Version incremented by Hibernate to reject stale concurrent writes. */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * Child rows owned by this aggregate persistence record.
     * Eager loading allows the adapter to reconstruct a complete aggregate at its boundary.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<JpaOrderLineEntity> lines = new ArrayList<>();

    /** Required by JPA; production code creates entities through the persistence mapper. */
    protected JpaOrderEntity() {
    }

    /** Creates a persistence record from explicit aggregate state. */
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
     * Maintains both sides of the JPA association before cascading persistence.
     *
     * @param line child persistence entity to attach
     */
    void addLine(JpaOrderLineEntity line) {
        lines.add(line);
        line.attachTo(this);
    }

    /** Returns the primary key. @return stored aggregate UUID */
    UUID id() { return id; }
    /** Returns the customer column. @return stored customer UUID */
    UUID customerId() { return customerId; }
    /** Returns the status column. @return stored lifecycle state */
    OrderStatus status() { return status; }
    /** Returns the payment reference column. @return stored payment reference */
    String paymentMethodId() { return paymentMethodId; }
    /** Returns the total column. @return stored aggregate total */
    BigDecimal total() { return total; }
    /** Returns the currency column. @return stored total currency */
    String currency() { return currency; }
    /** Returns the creation column. @return stored creation time */
    Instant createdAt() { return createdAt; }
    /** Returns the latest-update column. @return stored latest-update time */
    Instant updatedAt() { return updatedAt; }
    /** Returns the concurrency column. @return current optimistic-lock version */
    Long version() { return version; }
    /** Returns attached children without exposing mutable storage. @return immutable line view */
    List<JpaOrderLineEntity> lines() { return List.copyOf(lines); }
}

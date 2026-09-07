package com.orderflow.order.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Mutable JPA representation of one {@code order_lines} row owned by an order. */
@Entity
@Table(name = "order_lines")
public class JpaOrderLineEntity {
    /** Database-generated technical row key; it has no domain meaning. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning order row and foreign-key association. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private JpaOrderEntity order;

    /** Persisted product catalogue reference. */
    @Column(name = "product_id", nullable = false)
    private String productId;

    /** Persisted positive unit count. */
    @Column(nullable = false)
    private int quantity;

    /** Persisted non-negative price for one unit. */
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    /** ISO currency code accompanying the unit price. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Required by JPA; production code creates lines through the persistence mapper. */
    protected JpaOrderLineEntity() {
    }

    /** Creates a child persistence record from one domain line. */
    JpaOrderLineEntity(String productId, int quantity, BigDecimal unitPrice, String currency) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.currency = currency;
    }

    /**
     * Completes the child-to-parent side of the JPA association.
     *
     * @param order owning persistence entity
     */
    void attachTo(JpaOrderEntity order) {
        this.order = order;
    }

    /** Returns the product column. @return stored product reference */
    String productId() { return productId; }
    /** Returns the quantity column. @return stored quantity */
    int quantity() { return quantity; }
    /** Returns the unit-price column. @return stored unit price */
    BigDecimal unitPrice() { return unitPrice; }
    /** Returns the currency column. @return stored unit-price currency */
    String currency() { return currency; }
}

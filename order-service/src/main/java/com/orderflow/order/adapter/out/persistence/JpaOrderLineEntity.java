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

/** Representación JPA mutable de una fila de {@code order_lines} perteneciente a un pedido. */
@Entity
@Table(name = "order_lines")
public class JpaOrderLineEntity {
    /** Clave técnica de fila generada por la base de datos; carece de significado en el dominio. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fila propietaria del pedido y asociación mediante clave externa. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private JpaOrderEntity order;

    /** Referencia persistida del catálogo de productos. */
    @Column(name = "product_id", nullable = false)
    private String productId;

    /** Número positivo de unidades persistido. */
    @Column(nullable = false)
    private int quantity;

    /** Precio no negativo persistido de una unidad. */
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    /** Código ISO de la divisa que acompaña al precio unitario. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Requerido por JPA; el código de producción crea líneas mediante el mapeador de persistencia. */
    protected JpaOrderLineEntity() {
    }

    /** Crea un registro hijo de persistencia a partir de una línea del dominio. */
    JpaOrderLineEntity(String productId, int quantity, BigDecimal unitPrice, String currency) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.currency = currency;
    }

    /**
     * Completa el lado de hijo a padre de la asociación JPA.
     *
     * @param order entidad propietaria de persistencia
     */
    void attachTo(JpaOrderEntity order) {
        this.order = order;
    }

    /** Devuelve la columna de producto. @return referencia almacenada del producto */
    String productId() { return productId; }
    /** Devuelve la columna de cantidad. @return cantidad almacenada */
    int quantity() { return quantity; }
    /** Devuelve la columna de precio unitario. @return precio unitario almacenado */
    BigDecimal unitPrice() { return unitPrice; }
    /** Devuelve la columna de divisa. @return divisa almacenada del precio unitario */
    String currency() { return currency; }
}

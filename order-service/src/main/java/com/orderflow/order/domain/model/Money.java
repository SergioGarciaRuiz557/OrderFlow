package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Valor monetario utilizado para precios unitarios, subtotales y totales de pedidos.
 *
 * <p>El valor siempre utiliza {@link BigDecimal}, se redondea a dos decimales con
 * {@link RoundingMode#HALF_UP}, no puede ser negativo y actualmente solo admite EUR. Mantener la divisa
 * dentro del valor impide sumar importes expresados en divisas incompatibles.</p>
 *
 * @param amount importe monetario no nulo y no negativo
 * @param currency divisa del importe; actualmente debe ser EUR
 */
public record Money(BigDecimal amount, Currency currency) {
    /** Divisa que admite actualmente el contexto delimitado. */
    public static final Currency EUR = Currency.getInstance("EUR");

    /** Normaliza la escala y aplica las invariantes de divisa admitida e importe no negativo. */
    public Money {
        Objects.requireNonNull(amount, "Amount is required");
        Objects.requireNonNull(currency, "Currency is required");
        if (!EUR.equals(currency)) {
            throw new DomainInvariantViolationException("Only EUR is currently supported");
        }
        if (amount.signum() < 0) {
            throw new DomainInvariantViolationException("Money cannot be negative");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Factoría auxiliar para un importe en la divisa que admite actualmente el servicio.
     *
     * @param amount importe monetario
     * @return valor en EUR validado
     */
    public static Money eur(BigDecimal amount) {
        return new Money(amount, EUR);
    }

    /**
     * Crea una identidad aditiva en la divisa proporcionada para calcular el total.
     *
     * @param currency divisa que deben utilizar los operandos posteriores
     * @return cero en esa divisa
     */
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Suma otro importe después de comprobar que ambas divisas coincidan.
     *
     * @param other importe que se sumará
     * @return suma inmutable nueva
     * @throws DomainInvariantViolationException si las divisas son distintas
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /**
     * Multiplica un precio unitario por una cantidad positiva validada.
     *
     * @param quantity número de unidades
     * @return subtotal inmutable nuevo
     */
    public Money multiply(Quantity quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity.value())), currency);
    }

    /** Garantiza que las operaciones aritméticas nunca mezclen divisas de forma silenciosa. */
    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new DomainInvariantViolationException("Currencies must match");
        }
    }
}

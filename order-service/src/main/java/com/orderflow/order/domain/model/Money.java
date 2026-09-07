package com.orderflow.order.domain.model;

import com.orderflow.order.domain.exception.DomainInvariantViolationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Monetary value used for unit prices, subtotals, and order totals.
 *
 * <p>The value always uses {@link BigDecimal}, is rounded to two decimal places with
 * {@link RoundingMode#HALF_UP}, cannot be negative, and currently accepts EUR only. Keeping currency
 * inside the value prevents adding amounts expressed in incompatible currencies.</p>
 *
 * @param amount non-null, non-negative monetary amount
 * @param currency currency of the amount; currently must be EUR
 */
public record Money(BigDecimal amount, Currency currency) {
    /** Currency currently accepted by the bounded context. */
    public static final Currency EUR = Currency.getInstance("EUR");

    /** Normalizes scale and enforces the supported-currency and non-negative invariants. */
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
     * Convenience factory for an amount in the service's currently supported currency.
     *
     * @param amount monetary amount
     * @return validated EUR value
     */
    public static Money eur(BigDecimal amount) {
        return new Money(amount, EUR);
    }

    /**
     * Creates an additive identity in the supplied currency for total calculation.
     *
     * @param currency currency that subsequent operands must use
     * @return zero in that currency
     */
    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /**
     * Adds another amount after checking that both currencies match.
     *
     * @param other amount to add
     * @return new immutable sum
     * @throws DomainInvariantViolationException if currencies differ
     */
    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    /**
     * Multiplies a unit price by a validated positive quantity.
     *
     * @param quantity number of units
     * @return new immutable subtotal
     */
    public Money multiply(Quantity quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity.value())), currency);
    }

    /** Ensures arithmetic never silently mixes currencies. */
    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new DomainInvariantViolationException("Currencies must match");
        }
    }
}

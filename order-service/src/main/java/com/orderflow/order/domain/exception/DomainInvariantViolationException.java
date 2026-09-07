package com.orderflow.order.domain.exception;

public class DomainInvariantViolationException extends RuntimeException {
    public DomainInvariantViolationException(String message) {
        super(message);
    }
}

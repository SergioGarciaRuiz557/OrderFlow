package com.orderflow.order.domain.exception;

/**
 * Indicates that a value or requested Order transition violates a domain rule.
 *
 * <p>This exception belongs to the domain and therefore does not prescribe an HTTP status. The REST
 * adapter currently translates it to {@code 422 Unprocessable Entity}.</p>
 */
public class DomainInvariantViolationException extends RuntimeException {
    /**
     * Creates a failure containing a safe, business-oriented explanation.
     *
     * @param message invariant or transition rule that was violated
     */
    public DomainInvariantViolationException(String message) {
        super(message);
    }
}

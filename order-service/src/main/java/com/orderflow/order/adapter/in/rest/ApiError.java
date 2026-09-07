package com.orderflow.order.adapter.in.rest;

import java.time.Instant;

/**
 * Consistent JSON error contract returned by the REST exception handler.
 *
 * @param timestamp time at which the response was created
 * @param status numeric HTTP status
 * @param code stable machine-readable application code
 * @param message safe human-readable explanation
 * @param path request URI that failed
 */
public record ApiError(Instant timestamp, int status, String code, String message, String path) {
}

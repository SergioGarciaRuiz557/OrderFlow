package com.orderflow.order.adapter.in.rest;

import com.orderflow.order.application.exception.OrderNotFoundException;
import com.orderflow.order.domain.exception.DomainInvariantViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Converts failures from every REST controller into the stable {@link ApiError} contract.
 *
 * <p>Expected client and business errors retain useful messages. Infrastructure and unexpected
 * exceptions are logged with their stack traces but return deliberately generic messages so internal
 * implementation details are not exposed over HTTP.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    /** Server-side logger used only for failures that require operator investigation. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Creates the stateless controller advice discovered by Spring component scanning. */
    public ApiExceptionHandler() {
    }

    /**
     * Combines all Bean Validation field failures into one readable 400 response.
     *
     * @param exception validation result raised before controller execution
     * @param request servlet request used to report the failing path
     * @return structured invalid-request response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request);
    }

    /**
     * Handles malformed JSON and values that Spring cannot convert to controller argument types.
     *
     * @param exception parsing or conversion failure
     * @param request failing request
     * @return safe 400 response without parser internals
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformedRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is malformed", request);
    }

    /**
     * Maps rejected domain values or transitions to a semantic 422 response.
     *
     * @param exception domain rejection with a business-safe message
     * @param request failing request
     * @return structured domain-invariant response
     */
    @ExceptionHandler(DomainInvariantViolationException.class)
    ResponseEntity<ApiError> invariantViolation(DomainInvariantViolationException exception, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "DOMAIN_INVARIANT_VIOLATION", exception.getMessage(), request);
    }

    /**
     * Maps a missing aggregate to 404.
     *
     * @param exception application-level absence result
     * @param request failing request
     * @return structured not-found response
     */
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiError> notFound(OrderNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception.getMessage(), request);
    }

    /**
     * Logs database failures and hides SQL, credentials, and implementation details from clients.
     *
     * @param exception Spring's technology-neutral data-access failure
     * @param request failing request
     * @return safe infrastructure-failure response
     */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> infrastructureFailure(DataAccessException exception, HttpServletRequest request) {
        LOGGER.error("Order persistence failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INFRASTRUCTURE_FAILURE",
                "The order service is temporarily unavailable", request);
    }

    /**
     * Last-resort protection for failures not classified by a more specific handler.
     *
     * @param exception unexpected failure logged for operators
     * @param request failing request
     * @return generic internal-error response
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpectedFailure(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected order service failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
    }

    /**
     * Central factory that keeps every error payload structurally identical.
     *
     * @param status HTTP status and numeric payload value
     * @param code stable application error code
     * @param message safe detail for the client
     * @param request source of the failing URI
     * @return response entity with matching HTTP and body status
     */
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), code, message, request.getRequestURI()));
    }
}

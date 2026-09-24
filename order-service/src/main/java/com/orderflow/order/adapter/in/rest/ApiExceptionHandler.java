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
 * Convierte los fallos de todos los controladores REST al contrato estable {@link ApiError}.
 *
 * <p>Los errores esperados del cliente y de negocio conservan mensajes útiles. Las excepciones de infraestructura
 * e inesperadas se registran con sus trazas de pila, pero devuelven deliberadamente mensajes genéricos para que los
 * detalles internos de implementación no queden expuestos mediante HTTP.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    /** Registrador del servidor utilizado solo para fallos que requieren la investigación de un operador. */
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Crea el componente de asesoramiento del controlador sin estado que descubre el escaneo de componentes de Spring. */
    public ApiExceptionHandler() {
    }

    /**
     * Combina todos los fallos de campo de Bean Validation en una respuesta 400 legible.
     *
     * @param exception resultado de validación producido antes de ejecutar el controlador
     * @param request petición del servlet utilizada para informar de la ruta que ha fallado
     * @return respuesta estructurada de petición no válida
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, request);
    }

    /**
     * Gestiona el JSON mal formado y los valores que Spring no puede convertir a tipos de argumentos del controlador.
     *
     * @param exception fallo de análisis o conversión
     * @param request petición que ha fallado
     * @return respuesta 400 segura sin detalles internos del analizador
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformedRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request is malformed", request);
    }

    /**
     * Mapea valores o transiciones del dominio rechazados a una respuesta semántica 422.
     *
     * @param exception rechazo del dominio con un mensaje seguro para el negocio
     * @param request petición que ha fallado
     * @return respuesta estructurada de invariante del dominio
     */
    @ExceptionHandler(DomainInvariantViolationException.class)
    ResponseEntity<ApiError> invariantViolation(DomainInvariantViolationException exception, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "DOMAIN_INVARIANT_VIOLATION", exception.getMessage(), request);
    }

    /**
     * Mapea un agregado ausente a 404.
     *
     * @param exception resultado de ausencia del nivel de aplicación
     * @param request petición que ha fallado
     * @return respuesta estructurada de recurso no encontrado
     */
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiError> notFound(OrderNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception.getMessage(), request);
    }

    /**
     * Registra los fallos de la base de datos y oculta a los clientes el SQL, las credenciales y los detalles de implementación.
     *
     * @param exception fallo de acceso a datos de Spring independiente de la tecnología
     * @param request petición que ha fallado
     * @return respuesta segura de fallo de infraestructura
     */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> infrastructureFailure(DataAccessException exception, HttpServletRequest request) {
        LOGGER.error("Order persistence failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INFRASTRUCTURE_FAILURE",
                "The order service is temporarily unavailable", request);
    }

    /**
     * Protección de último recurso para los fallos que no clasifica un gestor más específico.
     *
     * @param exception fallo inesperado que se registra para los operadores
     * @param request petición que ha fallado
     * @return respuesta genérica de error interno
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpectedFailure(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected order service failure", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request);
    }

    /**
     * Factoría central que mantiene idéntica la estructura de todas las cargas útiles de error.
     *
     * @param status estado HTTP y valor numérico de la carga útil
     * @param code código estable de error de la aplicación
     * @param message detalle seguro para el cliente
     * @param request origen de la URI que ha fallado
     * @return entidad de respuesta cuyo estado HTTP coincide con el del cuerpo
     */
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), code, message, request.getRequestURI()));
    }
}

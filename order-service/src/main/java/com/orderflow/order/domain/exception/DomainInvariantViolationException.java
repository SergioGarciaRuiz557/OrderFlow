package com.orderflow.order.domain.exception;

/**
 * Indica que un valor o una transición solicitada de Order infringe una regla del dominio.
 *
 * <p>Esta excepción pertenece al dominio y, por tanto, no prescribe un estado HTTP. El adaptador REST
 * la traduce actualmente a {@code 422 Unprocessable Entity}.</p>
 */
public class DomainInvariantViolationException extends RuntimeException {
    /**
     * Crea un fallo que contiene una explicación segura y orientada al negocio.
     *
     * @param message invariante o regla de transición que se ha infringido
     */
    public DomainInvariantViolationException(String message) {
        super(message);
    }
}

package com.orderflow.order.adapter.in.rest;

import java.time.Instant;

/**
 * Contrato JSON coherente de error que devuelve el gestor de excepciones REST.
 *
 * @param timestamp instante en el que se creó la respuesta
 * @param status estado HTTP numérico
 * @param code código estable de la aplicación legible por máquinas
 * @param message explicación segura y legible por personas
 * @param path URI de la petición que ha fallado
 */
public record ApiError(Instant timestamp, int status, String code, String message, String path) {
}

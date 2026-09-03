package com.orderflow.inventory.application.port.`out`

import java.time.Instant

/**
 * Outbound application port that supplies the authoritative current time.
 *
 * Domain operations receive timestamps explicitly, keeping the domain deterministic. Production
 * uses a UTC system clock while tests can provide a fixed value without static mocking.
 */
fun interface ClockProvider {
    /**
     * Obtains the current instant for reservation lifecycle timestamps.
     *
     * @return current time from the configured clock source.
     */
    fun now(): Instant
}

package com.orderflow.inventory.configuration

import com.orderflow.inventory.application.port.`out`.ClockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * Dependency configuration for infrastructure-independent time handling.
 *
 * Keeping clock construction here lets the application depend on [ClockProvider] and lets tests
 * substitute deterministic clocks without changing domain behavior.
 */
@Configuration
class InventoryConfiguration {
    /**
     * Adapts Java's [Clock] to the application's small outbound time port.
     *
     * @param clock configured clock implementation.
     * PostgreSQL stores timestamps with microsecond precision, while [java.time.Instant] can carry
     * nanoseconds. Normalizing here ensures the value returned by a use case is identical to the
     * value reconstructed after a persistence round trip on every operating system.
     *
     * @return provider whose `now` operation returns the current instant at microsecond precision.
     */
    @Bean
    fun clockProvider(clock: Clock): ClockProvider = ClockProvider {
        clock.instant().truncatedTo(ChronoUnit.MICROS)
    }

    /**
     * Provides the production clock in UTC.
     *
     * UTC instants avoid server-time-zone ambiguity in persisted reservation timestamps.
     *
     * @return system clock configured for UTC.
     */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

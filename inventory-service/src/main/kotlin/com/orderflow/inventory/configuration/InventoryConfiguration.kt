package com.orderflow.inventory.configuration

import com.orderflow.inventory.application.port.`out`.ClockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

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
     * @return provider whose `now` operation delegates to [Clock.instant].
     */
    @Bean
    fun clockProvider(clock: Clock): ClockProvider = ClockProvider(clock::instant)

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

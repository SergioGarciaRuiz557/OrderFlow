package com.orderflow.payment.configuration

import com.orderflow.payment.application.port.`out`.ClockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * Spring dependency configuration for infrastructure-independent time handling.
 *
 * Domain and application classes depend on [ClockProvider], not directly on static system time. This
 * configuration supplies the production UTC clock while unit tests can inject deterministic mock
 * values without loading Spring.
 */
@Configuration
class PaymentConfiguration {
    /**
     * Provides the production wall clock in UTC.
     *
     * UTC instants avoid server-local time-zone ambiguity in persisted lifecycle timestamps.
     *
     * @return system clock configured for UTC.
     */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /**
     * Adapts Java's [Clock] to the application's minimal outbound time port.
     *
     * PostgreSQL `TIMESTAMPTZ` commonly stores microsecond precision while [java.time.Instant] can
     * carry nanoseconds. Truncating at the boundary makes the value returned immediately by a use case
     * equal to the value reconstructed after a persistence round trip on every supported platform.
     *
     * @param clock configured production or test clock.
     * @return provider whose `now` value is normalized to microsecond precision.
     */
    @Bean
    fun clockProvider(clock: Clock): ClockProvider = ClockProvider {
        clock.instant().truncatedTo(ChronoUnit.MICROS)
    }
}

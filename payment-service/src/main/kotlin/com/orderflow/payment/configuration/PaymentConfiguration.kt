package com.orderflow.payment.configuration

import com.orderflow.payment.application.port.`out`.ClockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.temporal.ChronoUnit

@Configuration
class PaymentConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun clockProvider(clock: Clock): ClockProvider = ClockProvider {
        clock.instant().truncatedTo(ChronoUnit.MICROS)
    }
}

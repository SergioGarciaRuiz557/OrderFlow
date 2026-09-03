package com.orderflow.inventory.configuration

import com.orderflow.inventory.application.port.`out`.ClockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class InventoryConfiguration {
    @Bean
    fun clockProvider(clock: Clock): ClockProvider = ClockProvider(clock::instant)

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

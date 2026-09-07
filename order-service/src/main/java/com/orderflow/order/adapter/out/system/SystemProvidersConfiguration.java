package com.orderflow.order.adapter.out.system;

import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.OrderIdGenerator;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/** Creates production implementations of nondeterministic application output ports. */
@Configuration
public class SystemProvidersConfiguration {
    /** Creates the stateless Spring configuration component. */
    public SystemProvidersConfiguration() {
    }

    /**
     * Uses the UTC system timeline for domain timestamps.
     *
     * @return production clock adapter
     */
    @Bean
    ClockProvider clockProvider() {
        return Instant::now;
    }

    /**
     * Generates random UUID-backed Order identities in production.
     *
     * @return production identity adapter
     */
    @Bean
    OrderIdGenerator orderIdGenerator() {
        return OrderId::newId;
    }
}

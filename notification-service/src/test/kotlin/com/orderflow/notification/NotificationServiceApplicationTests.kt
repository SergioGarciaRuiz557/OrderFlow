package com.orderflow.notification

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * Minimal Spring integration test for the complete Notification Service application context.
 *
 * Unit tests instantiate application services directly and validate their behavior in isolation.
 * This complementary test asks Spring Boot to discover and construct the real component graph,
 * proving that the application services, notification factory, and fake sender can be wired
 * together with the production configuration.
 *
 * [SpringBootTest] starts the application context without requiring a REST endpoint, Kafka broker,
 * database, or external email provider.
 */
@SpringBootTest
class NotificationServiceApplicationTests {

    /**
     * Verifies that Spring can start the service with all required beans and configuration.
     *
     * The body is intentionally empty: JUnit reaches it only after [SpringBootTest] has completed
     * context initialization. A missing bean, conflicting implementation, or invalid configuration
     * fails the test before this method returns.
     */
    @Test
    fun contextLoads() {
        // Successful entry into and return from this method means context creation succeeded.
    }
}

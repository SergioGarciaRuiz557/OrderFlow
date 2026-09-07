package com.orderflow.inventory.configuration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Unit tests for infrastructure-independent application configuration. */
class InventoryConfigurationTest {
    /** Ensures database round trips cannot change timestamps created by the application clock. */
    @Test
    fun `clock provider normalizes instants to PostgreSQL microsecond precision`() {
        val nanosecondInstant = Instant.parse("2026-01-01T00:00:00.123456789Z")
        val clock = Clock.fixed(nanosecondInstant, ZoneOffset.UTC)

        val normalizedInstant = InventoryConfiguration().clockProvider(clock).now()

        assertEquals(Instant.parse("2026-01-01T00:00:00.123456Z"), normalizedInstant)
    }
}

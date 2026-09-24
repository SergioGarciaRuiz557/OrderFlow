package com.orderflow.inventory.configuration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Pruebas unitarias de la configuración de aplicación independiente de la infraestructura. */
class InventoryConfigurationTest {
    /** Garantiza que los ciclos por la base de datos no cambien las marcas creadas por el reloj de aplicación. */
    @Test
    fun `el proveedor de reloj normaliza instantes a la precisión de microsegundos de PostgreSQL`() {
        val nanosecondInstant = Instant.parse("2026-01-01T00:00:00.123456789Z")
        val clock = Clock.fixed(nanosecondInstant, ZoneOffset.UTC)

        val normalizedInstant = InventoryConfiguration().clockProvider(clock).now()

        assertEquals(Instant.parse("2026-01-01T00:00:00.123456Z"), normalizedInstant)
    }
}

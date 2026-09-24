package com.orderflow.inventory.configuration

import com.orderflow.inventory.application.port.`out`.ClockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * Configuración de dependencias para gestionar el tiempo con independencia de la infraestructura.
 *
 * Mantener aquí la creación del reloj permite que la aplicación dependa de [ClockProvider] y que
 * las pruebas sustituyan los relojes por otros deterministas sin cambiar el comportamiento del dominio.
 */
@Configuration
class InventoryConfiguration {
    /**
     * Adapta el [Clock] de Java al pequeño puerto de salida temporal de la aplicación.
     *
     * @param clock implementación configurada del reloj.
     * PostgreSQL almacena marcas temporales con precisión de microsegundos, mientras que
     * [java.time.Instant] puede contener nanosegundos. Normalizar aquí garantiza que el valor
     * devuelto por un caso de uso sea idéntico al reconstruido tras un ciclo completo de
     * persistencia en cualquier sistema operativo.
     *
     * @return proveedor cuya operación `now` devuelve el instante actual con precisión de microsegundos.
     */
    @Bean
    fun clockProvider(clock: Clock): ClockProvider = ClockProvider {
        clock.instant().truncatedTo(ChronoUnit.MICROS)
    }

    /**
     * Proporciona el reloj de producción en UTC.
     *
     * Los instantes UTC evitan la ambigüedad de la zona horaria del servidor en las marcas temporales persistidas de las reservas.
     *
     * @return reloj del sistema configurado en UTC.
     */
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

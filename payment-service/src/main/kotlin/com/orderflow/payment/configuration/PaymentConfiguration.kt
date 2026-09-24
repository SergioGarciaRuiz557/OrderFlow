package com.orderflow.payment.configuration

import com.orderflow.payment.application.port.`out`.ClockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * Configuración de dependencias de Spring para gestionar el tiempo con independencia de la infraestructura.
 *
 * Las clases de dominio y aplicación dependen de [ClockProvider], no directamente de la hora estática
 * del sistema. Esta configuración proporciona el reloj UTC de producción, mientras que las pruebas
 * unitarias pueden inyectar valores simulados deterministas sin cargar Spring.
 */
@Configuration
class PaymentConfiguration {
    /**
     * Proporciona el reloj de pared de producción en UTC.
     *
     * Los instantes UTC evitan la ambigüedad de la zona horaria local del servidor en las marcas de tiempo conservadas del ciclo de vida.
     *
     * @return reloj del sistema configurado en UTC.
     */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    /**
     * Adapta [Clock] de Java al puerto mínimo de salida de tiempo de la aplicación.
     *
     * `TIMESTAMPTZ` de PostgreSQL suele almacenar precisión de microsegundos, mientras que
     * [java.time.Instant] puede contener nanosegundos. Truncar en el límite hace que el valor devuelto
     * inmediatamente por un caso de uso sea igual al reconstruido tras un ciclo completo de
     * persistencia en todas las plataformas admitidas.
     *
     * @param clock reloj configurado de producción o de pruebas.
     * @return proveedor cuyo valor `now` se normaliza con precisión de microsegundos.
     */
    @Bean
    fun clockProvider(clock: Clock): ClockProvider = ClockProvider {
        clock.instant().truncatedTo(ChronoUnit.MICROS)
    }
}

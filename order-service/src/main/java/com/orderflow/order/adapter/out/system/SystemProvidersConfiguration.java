package com.orderflow.order.adapter.out.system;

import com.orderflow.order.application.port.out.ClockProvider;
import com.orderflow.order.application.port.out.OrderIdGenerator;
import com.orderflow.order.domain.model.OrderId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/** Crea implementaciones de producción de los puertos de salida no deterministas de la aplicación. */
@Configuration
public class SystemProvidersConfiguration {
    /** Crea el componente de configuración de Spring sin estado. */
    public SystemProvidersConfiguration() {
    }

    /**
     * Utiliza la línea temporal UTC del sistema para las marcas temporales del dominio.
     *
     * @return adaptador de reloj de producción
     */
    @Bean
    ClockProvider clockProvider() {
        return Instant::now;
    }

    /**
     * Genera en producción identidades aleatorias de Order respaldadas por UUID.
     *
     * @return adaptador de identidad de producción
     */
    @Bean
    OrderIdGenerator orderIdGenerator() {
        return OrderId::newId;
    }
}

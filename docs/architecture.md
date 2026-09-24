# Arquitectura de OrderFlow

OrderFlow se organiza en contextos delimitados independientes. Cada microservicio sigue la misma convención de paquetes de Arquitectura Hexagonal, pero conserva su propio ciclo de compilación y su propio modelo de dominio.

## Dirección de las dependencias

Las dependencias apuntan hacia dentro:

```text
Adaptador -> Aplicación -> Dominio
```

El dominio no depende de la aplicación ni de los adaptadores. La aplicación no depende de implementaciones concretas de los adaptadores.

Kafka es infraestructura externa y solo está presente en los paquetes de adaptadores y configuración:

```text
REST -> Adaptador de Order -> Aplicación -> Dominio
                              |
                              v
                    Adaptador de salida Kafka -> Kafka

Kafka -> Adaptador de entrada Kafka -> Aplicación -> Dominio
```

Los consumidores validan y mapean los contratos JSON antes de invocar los puertos de entrada. Los productores mapean los resultados del dominio y de la aplicación a DTO de transporte locales. Ni el dominio ni la aplicación importan APIs de Kafka, conocen los nombres de los topics o serializan directamente sus modelos.

## Convenciones de paquetes

### `domain`

Contiene las reglas y los conceptos de negocio. Sus subpaquetes se reservan para agregados, entidades y objetos de valor (`model`), eventos de dominio (`event`), errores específicos del dominio (`exception`) y servicios de dominio (`service`). Los servicios de dominio solo son apropiados cuando un comportamiento no puede pertenecer de forma natural a un agregado u objeto de valor.

### `application`

Coordina los casos de uso sin comportamientos específicos de infraestructura. `port.in` contiene los puertos de entrada que exponen casos de uso, `port.out` contiene los puertos de salida que requieren esos casos de uso y `service` contiene las futuras implementaciones de casos de uso que coordinan puertos y objetos de dominio.

### `adapter`

Conecta los puertos de aplicación con tecnologías y mecanismos de entrega. Los adaptadores de entrada residirán en `in.rest` e `in.kafka`. Los adaptadores de salida residirán en `out.persistence`, `out.kafka` y `out.external`.

La Arquitectura Hexagonal no sitúa lógica de negocio en los adaptadores. Los adaptadores traducen entre aspectos externos y puertos de aplicación; las reglas de negocio permanecen en el dominio.

## Estado actual

Order, Inventory y Payment persisten su estado de dominio en PostgreSQL. Kafka conecta los cuatro servicios de forma asíncrona mediante cinco topics orientados a contextos delimitados. Las funcionalidades de fiabilidad que exceden un transporte con capacidad at-least-once se han pospuesto deliberadamente; consulte las [limitaciones de mensajería](messaging/README.md#limitaciones-conocidas).

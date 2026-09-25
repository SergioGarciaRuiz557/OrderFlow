# Servicio de inventario

El contexto delimitado de Inventario es responsable de las existencias disponibles de cada producto y del ciclo de vida de las reservas de existencias. Puede ejecutarse de forma independiente: los administradores pueden preparar y consultar el inventario mediante REST, mientras que los puertos de aplicación proporcionan operaciones de reserva y liberación para futuros adaptadores de mensajería.

## Arquitectura

El servicio utiliza DDD con límites hexagonales:

- `domain/model` contiene el comportamiento de negocio independiente de frameworks.
- `application/port/in` define los casos de uso y `application/port/out` define los contratos de infraestructura.
- `application/service` coordina la carga de agregados, el comportamiento, la persistencia y los reintentos acotados por concurrencia.
- `adapter/in/rest` expone la API de administración.
- `adapter/out/persistence` asigna explícitamente el dominio a entidades JPA de Spring Data.

`InventoryItem` es la raíz del agregado. Posee la cantidad disponible actual y todas las entidades `StockReservation` de un `ProductId`; las reservas nunca se modifican independientemente de su elemento de inventario. `StockReservation` registra el identificador de la reserva, el identificador del pedido, la cantidad, el estado, la hora de reserva y la hora de liberación opcional. Este historial permite rastrear la asignación y la compensación, en lugar de representar una reserva como un decremento entero sin explicación.

## Invariantes e idempotencia

- Las existencias disponibles no pueden ser negativas.
- Una `Quantity` de reserva siempre es positiva.
- Una reserva no puede superar las existencias disponibles.
- Un par producto/pedido tiene como máximo una reserva, restricción aplicada tanto en el agregado como en PostgreSQL.
- Repetir la misma solicitud de reserva activa con el mismo pedido y la misma cantidad devuelve la reserva existente como resultado idempotente satisfactorio.
- Reutilizar un pedido con una cantidad diferente, o después de que se haya liberado su reserva, se rechaza explícitamente.
- Liberar una reserva ya liberada es un resultado idempotente explícito y nunca repone las existencias dos veces.
- Liberar una reserva desconocida devuelve `ReservationNotFound`; no se acepta silenciosamente.

Los resultados de reserva y liberación son jerarquías selladas de Kotlin. Los consumidores usan expresiones `when` exhaustivas y nunca deducen un rechazo de negocio a partir de `null` o de una excepción de infraestructura.

## Diseño en Kotlin

Los identificadores y la `Quantity` positiva usan tipos `@JvmInline value class`. Las transiciones del agregado devuelven nuevos valores inmutables, los resultados de reserva son interfaces selladas, los valores anulables se limitan a estados realmente opcionales y la implementación favorece las expresiones directas frente a patrones JavaBean o cadenas densas de funciones de ámbito.

## Persistencia y concurrencia

Flyway administra PostgreSQL. Las entidades JPA son modelos exclusivos de persistencia y un asignador explícito reconstruye el agregado de dominio. Ni Spring Data ni los tipos JPA atraviesan el puerto de salida.

`inventory_items.version` se asigna con `@Version`. Cada reserva o liberación se evalúa sobre un agregado cargado y se guarda de forma optimista. Si otra transacción gana la carrera, el servicio de aplicación vuelve a cargar el estado y a evaluar la regla de negocio (hasta tres intentos). Por tanto, cuando dos pedidos compiten por la última unidad, se obtiene una reserva y un rechazo por existencias insuficientes, en lugar de vender más unidades de las disponibles. La restricción de unicidad de la base de datos sobre `(product_id, order_id)` constituye una segunda línea de defensa contra la creación de reservas duplicadas.

Las pruebas de integración usan una instancia desechable de PostgreSQL con Testcontainers para verificar Flyway, los ciclos completos de persistencia, la detección de escrituras obsoletas y la reserva concurrente de la última unidad. Solo se omiten cuando Docker no está disponible.

## API REST de administración

Establece la cantidad disponible actual (se admite cero):

```http
PUT /api/inventory/{productId}
Content-Type: application/json

{
  "quantity": 10
}
```

Consulta el inventario y su historial de reservas:

```http
GET /api/inventory/{productId}
```

`PUT` establece directamente la cantidad disponible y está pensado para la preparación administrativa o de desarrollo de las existencias. No reescribe el historial de reservas. La reserva y la liberación se exponen deliberadamente como API de aplicación, en lugar de como endpoints REST provisionales de producción.

## Ejecución

La ejecución local requiere Docker. Desde la raíz, ejecute `docker compose up -d --wait postgres kafka` para iniciar la infraestructura compartida y después arranque Inventory desde el IDE o Gradle. La gestión automática de Compose en Spring Boot está desactivada para evitar que una aplicación controle los servidores de los demás servicios. También puede ejecutar `docker compose up -d --build inventory-service` desde la raíz para arrancar Inventory y sus dependencias en contenedores. Consulte la [guía de ejecución local](../README.md#ejecución-local). El Compose de este directorio queda como alternativa aislada y no debe arrancarse junto al de la raíz.

La conexión predeterminada es `jdbc:postgresql://localhost:5432/inventory`, con nombre de usuario y contraseña `inventory`. Se puede sustituir mediante `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` al conectarse a una base de datos administrada externamente.

```shell
./gradlew test
./gradlew bootRun
```

## Integración con Kafka

El grupo `inventory-service.commands` consume `ReserveInventoryCommand` y `ReleaseInventoryCommand` desde `order.inventory.commands`. Los adaptadores ligeros validan y asignan los DTO JSON locales e invocan los puertos de entrada de Inventario. Los resultados definitivos se publican a través de `InventoryEventPublisher` como `InventoryReservedEvent`, `InventoryRejectedEvent` o `InventoryReleasedEvent` en `inventory.order.events`; el rechazo por existencias es un evento de negocio normal, no una excepción técnica.

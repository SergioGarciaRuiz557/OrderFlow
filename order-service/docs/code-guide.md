# Guía del código del servicio de pedidos

Esta guía explica cómo recorre una petición Order Service y qué responsabilidad corresponde a
cada clase. Complementa el Javadoc situado junto a la implementación; no sustituye el lenguaje
de dominio ni la documentación de la API incluidos en el README del servicio.

## Lectura del servicio desde fuera hacia dentro

El servicio sigue una arquitectura hexagonal. Un orden de lectura útil es:

1. `adapter/in/rest` recibe y valida los datos HTTP.
2. `application/port/in` declara las capacidades de negocio disponibles para los adaptadores de entrada.
3. `application/service` coordina un caso de uso sin volver a implementar las reglas de dominio.
4. `domain/model` valida valores, calcula totales y controla las transiciones del pedido.
5. `application/port/out` declara las capacidades de infraestructura que necesita la aplicación.
6. `adapter/out/persistence` mapea el agregado a JPA y PostgreSQL.
7. `adapter/in/kafka` y `adapter/out/kafka` mapean mensajes JSON versionados desde y hacia esos puertos.

La dirección de las dependencias siempre apunta hacia el dominio. Las clases de dominio no importan
Spring, JPA, HTTP, PostgreSQL ni Kafka.

## Creación y consulta de un pedido

### `POST /api/orders`

1. Jackson convierte el JSON en `CreateOrderRequest`.
2. Bean Validation rechaza identificadores ausentes, colecciones de artículos vacías, cantidades no positivas
   y precios negativos antes de que el controlador invoque la aplicación.
3. `OrderController` mapea los valores de transporte a `CreateOrderCommand` y llama a
   `CreateOrderUseCase`.
4. `CreateOrderService` convierte los valores primitivos en objetos de valor del dominio. Esos objetos validan
   las mismas invariantes esenciales con independencia de la validación HTTP.
5. `Order.create` calcula el total y registra `OrderCreated`.
6. La aplicación pide al agregado que solicite inventario. El agregado pasa de `PENDING` a
   `INVENTORY_RESERVATION_PENDING` y registra `InventoryReservationRequested`.
7. `JpaOrderRepositoryAdapter` mapea y guarda el agregado completo en una sola transacción.
8. `IntegrationMessagePublisher` recibe los eventos de dominio pendientes y el adaptador de Kafka mapea los
   eventos admitidos a comandos o eventos de integración cuya clave es el pedido.
9. El controlador devuelve `201 Created`, la ubicación del recurso y `OrderResponse`.

### `GET /api/orders/{orderId}`

1. Spring convierte el valor de la ruta en `UUID`.
2. `GetOrderService` crea un `OrderId` y carga el agregado mediante el puerto de repositorio.
3. El mapeador de persistencia reconstruye el agregado con `Order.rehydrate`. La rehidratación vuelve a calcular
   el total de las líneas y rechaza un estado persistido corrupto, pero no recrea eventos históricos.
4. `OrderView` lleva la salida de la aplicación al controlador y `OrderResponse` la transporta mediante HTTP.

## Notificaciones asíncronas del ciclo de vida

Los listeners de Kafka validan los sobres de Inventory y Payment y llaman a cinco límites de la aplicación:

| Puerto de entrada | Servicio de aplicación | Comportamiento del agregado | Estado resultante |
| --- | --- | --- | --- |
| `HandleInventoryReservedUseCase` | `HandleInventoryReservedService` | Marca el inventario como reservado y después solicita el pago | `PAYMENT_PENDING` |
| `HandleInventoryRejectedUseCase` | `HandleInventoryRejectedService` | Registra el rechazo y la cancelación | `CANCELLED` |
| `HandlePaymentAuthorizedUseCase` | `HandlePaymentAuthorizedService` | Registra la autorización y la confirmación | `CONFIRMED` |
| `HandlePaymentRejectedUseCase` | `HandlePaymentRejectedService` | Registra el rechazo y solicita la liberación del inventario | `CANCELLATION_PENDING` |
| `HandleInventoryReleasedUseCase` | `HandleInventoryReleasedService` | Completa la cancelación tras la liberación | `CANCELLED` |

Los gestores cargan, llaman al comportamiento del dominio, guardan y publican, en ese orden. No asignan estados
directamente. El agregado gestiona las notificaciones repetidas para que la idempotencia sea coherente para cualquier
adaptador de entrada posible.

## Catálogo de clases

### Arranque

- `OrderServiceApplication`: punto de entrada de Spring Boot y raíz del escaneo de componentes.

### Modelo de dominio

- `Order`: raíz del agregado. Posee las líneas, el total, el estado del ciclo de vida, las marcas temporales,
  la versión de bloqueo optimista y los eventos de dominio no confirmados.
- `OrderLine`: tupla inmutable de producto, cantidad y precio unitario; calcula su subtotal.
- `OrderStatus`: conjunto exhaustivo de estados persistidos del ciclo de vida.
- `OrderId`, `CustomerId`, `ProductId`, `PaymentMethodId`: identificadores con tipo que evitan intercambiar
  accidentalmente cadenas o UUID que no guardan relación.
- `Quantity`: objeto de valor entero positivo.
- `Money`: objeto de valor con importe `BigDecimal` no negativo y divisa; actualmente solo admite EUR.
- `DomainInvariantViolationException`: señala entradas o transiciones rechazadas por las reglas del dominio.

### Eventos de dominio

- `OrderDomainEvent`: contrato común de evento que contiene la identidad del agregado y el instante en que ocurrió.
- `OrderCreated`: un agregado se ha creado correctamente.
- `InventoryReservationRequested`: debe intentarse reservar el inventario.
- `InventoryReserved`: Inventory ha aceptado la reserva.
- `InventoryRejected`: Inventory ha rechazado la reserva y proporciona un motivo.
- `PaymentAuthorizationRequested`: debe intentarse autorizar el pago.
- `PaymentAuthorized`: Payment ha aceptado la autorización.
- `PaymentRejected`: Payment ha rechazado la autorización y proporciona un motivo.
- `InventoryReleaseRequested`: debe compensarse o liberarse el inventario reservado previamente.
- `OrderConfirmed`: el pedido ha completado el flujo previo a la confirmación.
- `OrderCancelled`: el pedido ha alcanzado su estado terminal de cancelación.

Los eventos son hechos del dominio, no cargas útiles de Kafka. Los adaptadores de transporte son responsables de traducirlos
a contratos de integración versionados.

### Entrada y salida de la aplicación

- `CreateOrderUseCase` y sus records de comandos: límite de creación utilizado por REST.
- `GetOrderUseCase`: límite de consulta utilizado por REST.
- `HandleInventoryReservedUseCase`, `HandleInventoryRejectedUseCase`,
  `HandlePaymentAuthorizedUseCase` y `HandlePaymentRejectedUseCase`: límites de notificación destinados
  a los adaptadores de mensajería.
- `OrderView`: resultado estable de la aplicación, independiente de HTTP y la persistencia.
- `OrderRepository`: límite de persistencia expresado en términos del agregado.
- `IntegrationMessagePublisher`: límite de salida para publicar eventos sin tipos de Kafka.
- `ClockProvider`: proporciona el tiempo de forma determinista en las pruebas.
- `OrderIdGenerator`: proporciona identificadores del agregado de forma determinista en las pruebas.
- `OrderNotFoundException`: resultado de ausencia del nivel de aplicación utilizado por todos los adaptadores de entrada.

### Servicios de aplicación

- `CreateOrderService`: construye el agregado, solicita inventario, guarda y publica.
- `GetOrderService`: carga un agregado y devuelve una vista de la aplicación.
- `HandleInventoryReservedService`: hace avanzar una reserva correcta hasta el pago pendiente.
- `HandleInventoryRejectedService`: cancela un pedido rechazado por Inventory.
- `HandlePaymentAuthorizedService`: confirma un pedido tras un pago correcto.
- `HandlePaymentRejectedService`: inicia la compensación tras un pago fallido.
- `OrderApplicationSupport`: auxiliar de implementación con visibilidad de paquete para mantener coherentes la carga y la
  orquestación de guardar y después publicar.

### Adaptador REST

- `CreateOrderRequest`: modelo JSON de entrada validado.
- `OrderResponse`: modelo JSON de salida y mapeador de la aplicación al transporte.
- `OrderController`: definición ligera de rutas y mapeo de peticiones y respuestas.
- `ApiError`: estructura estable de la respuesta de error.
- `ApiExceptionHandler`: mapeo central de excepciones a HTTP que evita exponer trazas de pila.

### Adaptadores de persistencia e infraestructura

- `JpaOrderEntity`: representación JPA de la tabla `orders` y propietaria de las entidades de línea persistidas.
- `JpaOrderLineEntity`: representación JPA de una fila de `order_lines`.
- `SpringDataOrderRepository`: mecanismo CRUD interno de Spring Data; nunca atraviesa el adaptador.
- `OrderPersistenceMapper`: mapeo explícito y bidireccional entre las entidades JPA y el agregado.
- `JpaOrderRepositoryAdapter`: implementa el puerto de repositorio de la aplicación mediante Spring Data.
- `NoOpIntegrationMessagePublisher`: sumidero de mensajería local utilizado hasta que existe un adaptador de Kafka.
- `SystemProvidersConfiguration`: beans de producción para la hora actual y la generación de UUID aleatorios.

## Detalles de persistencia

`V1__create_orders.sql` es la fuente de verdad del esquema inicial. Hibernate utiliza
`ddl-auto=validate`, por lo que comprueba los mapeos, pero nunca crea ni modifica tablas de producción. La
columna `orders.version` se mapea con `@Version`; por ello, las actualizaciones concurrentes que utilizan un agregado obsoleto
fallan en lugar de sobrescribir silenciosamente un estado más reciente.

`JpaOrderEntity` y `JpaOrderLineEntity` contienen deliberadamente campos mutables orientados a la persistencia
y constructores protegidos sin argumentos requeridos por JPA. No son entidades de dominio y no deben
devolverse desde los puertos de la aplicación.

## Flujo de errores

`ApiExceptionHandler` clasifica los errores de la siguiente manera:

- JSON no válido, tipos de ruta no válidos y fallos de Bean Validation: `400 INVALID_REQUEST`;
- invariantes o transiciones del dominio rechazadas: `422 DOMAIN_INVARIANT_VIOLATION`;
- agregados ausentes: `404 ORDER_NOT_FOUND`;
- fallos de acceso a datos de Spring: `500 INFRASTRUCTURE_FAILURE`;
- cualquier excepción inesperada: `500 INTERNAL_ERROR`.

Las excepciones de infraestructura se registran en el servidor. Los clientes HTTP reciben mensajes estables y nunca
trazas de pila de Java.

## Pruebas

- `OrderTest` prueba la creación del agregado, los totales, las invariantes, las transiciones, los eventos de compensación
  y las notificaciones duplicadas sin Spring.
- `CreateOrderServiceTest` verifica la orquestación de la creación con puertos de salida simulados.
- `HandleInventoryReservedServiceTest` verifica la orquestación correcta del inventario con puertos simulados.
- `OrderServiceIntegrationTest` inicia PostgreSQL con Testcontainers y verifica Flyway, la persistencia y rehidratación
  del agregado, la creación y consulta REST, y los errores de validación estructurados.

El contenedor de integración es desechable e independiente de la base de datos persistente de desarrollo definida en
`compose.yaml`.

# Order Service code guide

This guide explains how a request travels through Order Service and what responsibility belongs to
each class. It complements the Javadoc located next to the implementation; it does not replace the
domain language and API documentation in the service README.

## Reading the service from the outside in

The service follows hexagonal architecture. A useful reading order is:

1. `adapter/in/rest` receives and validates HTTP data.
2. `application/port/in` declares the business capabilities available to inbound adapters.
3. `application/service` coordinates a use case without reimplementing domain rules.
4. `domain/model` validates values, calculates totals, and controls order transitions.
5. `application/port/out` declares the infrastructure capabilities required by the application.
6. `adapter/out/persistence` maps the aggregate to JPA and PostgreSQL.
7. `adapter/out/messaging` is the replaceable boundary for future Kafka publication.

The dependency direction always points towards the domain. Domain classes have no Spring, JPA,
HTTP, PostgreSQL, or Kafka imports.

## Creating and retrieving an order

### `POST /api/orders`

1. Jackson converts JSON into `CreateOrderRequest`.
2. Bean Validation rejects missing identifiers, empty item collections, non-positive quantities,
   and negative prices before the controller invokes the application.
3. `OrderController` maps transport values to `CreateOrderCommand` and calls
   `CreateOrderUseCase`.
4. `CreateOrderService` converts primitives into domain value objects. Those value objects validate
   the same essential invariants independently of HTTP validation.
5. `Order.create` calculates the total and records `OrderCreated`.
6. The application asks the aggregate to request inventory. The aggregate moves from `PENDING` to
   `INVENTORY_RESERVATION_PENDING` and records `InventoryReservationRequested`.
7. `JpaOrderRepositoryAdapter` maps and saves the complete aggregate in one transaction.
8. `IntegrationMessagePublisher` receives the pending domain events. The current adapter is a no-op;
   a later Kafka adapter can replace it without changing the use case.
9. The controller returns `201 Created`, the resource location, and `OrderResponse`.

### `GET /api/orders/{orderId}`

1. Spring converts the path value to `UUID`.
2. `GetOrderService` creates an `OrderId` and loads through the repository port.
3. The persistence mapper reconstructs the aggregate with `Order.rehydrate`. Rehydration recalculates
   the line total and rejects corrupt persisted state, but does not recreate historical events.
4. `OrderView` carries application output to the controller and `OrderResponse` carries it over HTTP.

## Asynchronous lifecycle callbacks

There are no Kafka adapters yet. The four handler input ports are nevertheless complete application
boundaries that a future consumer can call:

| Input port | Application service | Aggregate behavior | Resulting state |
| --- | --- | --- | --- |
| `HandleInventoryReservedUseCase` | `HandleInventoryReservedService` | Marks inventory reserved, then requests payment | `PAYMENT_PENDING` |
| `HandleInventoryRejectedUseCase` | `HandleInventoryRejectedService` | Records rejection and cancellation | `CANCELLED` |
| `HandlePaymentAuthorizedUseCase` | `HandlePaymentAuthorizedService` | Records authorization and confirmation | `CONFIRMED` |
| `HandlePaymentRejectedUseCase` | `HandlePaymentRejectedService` | Records rejection and asks for inventory release | `CANCELLATION_PENDING` |

Handlers load, call domain behavior, save, and publish in that order. They do not assign statuses
directly. Repeated callbacks are handled by the aggregate so idempotency remains consistent for every
possible inbound adapter.

## Class catalogue

### Bootstrap

- `OrderServiceApplication`: Spring Boot entry point and component-scanning root.

### Domain model

- `Order`: aggregate root. Owns lines, total, lifecycle state, timestamps, optimistic-lock version,
  and uncommitted domain events.
- `OrderLine`: immutable product, quantity, and unit-price tuple; calculates its subtotal.
- `OrderStatus`: exhaustive set of persisted lifecycle states.
- `OrderId`, `CustomerId`, `ProductId`, `PaymentMethodId`: typed identifiers that prevent unrelated
  strings or UUIDs from being accidentally interchanged.
- `Quantity`: positive integer value object.
- `Money`: non-negative `BigDecimal` amount and currency value object; currently accepts EUR only.
- `DomainInvariantViolationException`: signals input or transitions rejected by domain rules.

### Domain events

- `OrderDomainEvent`: common event contract containing the aggregate identity and occurrence time.
- `OrderCreated`: an aggregate was created successfully.
- `InventoryReservationRequested`: inventory reservation must be attempted.
- `InventoryReserved`: inventory accepted the reservation.
- `InventoryRejected`: inventory rejected the reservation and supplies a reason.
- `PaymentAuthorizationRequested`: payment authorization must be attempted.
- `PaymentAuthorized`: payment accepted the authorization.
- `PaymentRejected`: payment rejected the authorization and supplies a reason.
- `InventoryReleaseRequested`: previously reserved inventory must be compensated/released.
- `OrderConfirmed`: the order completed the pre-confirmation workflow.
- `OrderCancelled`: the order reached its terminal cancelled state.

Events are domain facts, not Kafka payloads. Transport adapters are responsible for translating them
to versioned integration contracts later.

### Application input and output

- `CreateOrderUseCase` and its command records: creation boundary used by REST.
- `GetOrderUseCase`: query boundary used by REST.
- `HandleInventoryReservedUseCase`, `HandleInventoryRejectedUseCase`,
  `HandlePaymentAuthorizedUseCase`, and `HandlePaymentRejectedUseCase`: callback boundaries intended
  for future messaging adapters.
- `OrderView`: stable application result independent of HTTP and persistence.
- `OrderRepository`: persistence boundary expressed in aggregate terms.
- `IntegrationMessagePublisher`: outbound event-publication boundary without Kafka types.
- `ClockProvider`: supplies time deterministically in tests.
- `OrderIdGenerator`: supplies aggregate identifiers deterministically in tests.
- `OrderNotFoundException`: application-level absence result used by all inbound adapters.

### Application services

- `CreateOrderService`: constructs the aggregate, requests inventory, saves, and publishes.
- `GetOrderService`: loads an aggregate and returns an application view.
- `HandleInventoryReservedService`: advances a successful reservation to payment pending.
- `HandleInventoryRejectedService`: cancels an order rejected by inventory.
- `HandlePaymentAuthorizedService`: confirms an order after successful payment.
- `HandlePaymentRejectedService`: begins compensation after failed payment.
- `OrderApplicationSupport`: package-private implementation helper for consistent loading and
  save-then-publish orchestration.

### REST adapter

- `CreateOrderRequest`: validated inbound JSON model.
- `OrderResponse`: outbound JSON model and application-to-transport mapper.
- `OrderController`: thin route definition and request/response mapping.
- `ApiError`: stable error response structure.
- `ApiExceptionHandler`: central exception-to-HTTP mapping that prevents stack-trace exposure.

### Persistence and infrastructure adapters

- `JpaOrderEntity`: JPA representation of the `orders` table and owner of persisted line entities.
- `JpaOrderLineEntity`: JPA representation of one `order_lines` row.
- `SpringDataOrderRepository`: internal Spring Data CRUD mechanism; never crosses the adapter.
- `OrderPersistenceMapper`: bidirectional, explicit mapping between JPA entities and the aggregate.
- `JpaOrderRepositoryAdapter`: implements the application repository port using Spring Data.
- `NoOpIntegrationMessagePublisher`: local messaging sink used until a Kafka adapter exists.
- `SystemProvidersConfiguration`: production beans for current time and random UUID generation.

## Persistence details

`V1__create_orders.sql` is the source of truth for the initial schema. Hibernate uses
`ddl-auto=validate`, so it checks mappings but never creates or mutates production tables. The
`orders.version` column is mapped with `@Version`; concurrent updates using an outdated aggregate
therefore fail instead of silently overwriting newer state.

`JpaOrderEntity` and `JpaOrderLineEntity` intentionally contain persistence-oriented mutable fields
and protected no-argument constructors required by JPA. They are not domain entities and must not be
returned from application ports.

## Error flow

`ApiExceptionHandler` classifies errors as follows:

- invalid JSON, invalid path types, and Bean Validation failures: `400 INVALID_REQUEST`;
- rejected domain invariants or transitions: `422 DOMAIN_INVARIANT_VIOLATION`;
- missing aggregates: `404 ORDER_NOT_FOUND`;
- Spring data-access failures: `500 INFRASTRUCTURE_FAILURE`;
- any unexpected exception: `500 INTERNAL_ERROR`.

Infrastructure exceptions are logged on the server. HTTP clients receive stable messages and never
Java stack traces.

## Tests

- `OrderTest` exercises aggregate creation, totals, invariants, transitions, compensation events,
  and duplicate callbacks without Spring.
- `CreateOrderServiceTest` verifies creation orchestration with mocked output ports.
- `HandleInventoryReservedServiceTest` verifies successful inventory orchestration with mocked ports.
- `OrderServiceIntegrationTest` starts PostgreSQL with Testcontainers and verifies Flyway, aggregate
  persistence/rehydration, REST creation/retrieval, and structured validation errors.

The integration container is disposable and separate from the persistent development database in
`compose.yaml`.

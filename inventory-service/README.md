# Inventory Service

The Inventory bounded context owns the available stock for each product and the lifecycle of stock reservations. It can run independently: administrators can prepare and inspect inventory over REST, while application ports provide reservation and release operations for future message adapters.

## Architecture

The service uses DDD with hexagonal boundaries:

- `domain/model` contains framework-free business behavior.
- `application/port/in` defines use cases and `application/port/out` defines infrastructure contracts.
- `application/service` orchestrates aggregate loading, behavior, persistence, and bounded concurrency retries.
- `adapter/in/rest` exposes the administration API.
- `adapter/out/persistence` maps the domain explicitly to Spring Data JPA entities.

`InventoryItem` is the aggregate root. It owns the current available quantity and all `StockReservation` entities for one `ProductId`; reservations are never changed independently of their inventory item. `StockReservation` records the reservation id, order id, quantity, status, reservation time, and optional release time. This history makes allocation and compensation traceable instead of representing reservation as an unexplained integer decrement.

## Invariants and idempotency

- Available stock cannot be negative.
- A reservation `Quantity` is always positive.
- A reservation cannot exceed available stock.
- A product/order pair has at most one reservation, enforced in both the aggregate and PostgreSQL.
- Repeating the same active reservation request with the same order and quantity returns the existing reservation as an idempotent success.
- Reusing an order with a different quantity, or after its reservation has been released, is explicitly rejected.
- Releasing an already released reservation is an explicit idempotent outcome and never restores stock twice.
- Releasing an unknown reservation returns `ReservationNotFound`; it is not silently accepted.

Reservation and release outcomes are sealed Kotlin hierarchies. Callers use exhaustive `when` expressions and never infer a business rejection from `null` or an infrastructure exception.

## Kotlin design

Identifiers and positive `Quantity` use `@JvmInline value class` types. Aggregate transitions return new immutable values, reservation results are sealed interfaces, nullable values are limited to genuinely optional state, and the implementation favors direct expressions over JavaBean patterns or dense scope-function chains.

## Persistence and concurrency

PostgreSQL is managed by Flyway. The JPA entities are persistence-only models and an explicit mapper reconstructs the domain aggregate. Neither Spring Data nor JPA types cross the output port.

`inventory_items.version` is mapped with `@Version`. Each reservation or release is evaluated against a loaded aggregate and saved optimistically. If another transaction wins the race, the application service reloads and evaluates the business rule again (up to three attempts). Consequently, two orders racing for the final unit yield one reservation and one insufficient-stock rejection rather than overselling. The database uniqueness constraint on `(product_id, order_id)` is a second line of defense against duplicate reservation creation.

Integration tests use a disposable Testcontainers PostgreSQL instance to verify Flyway, persistence round trips, stale-write detection, and concurrent final-unit reservation. They are skipped only when Docker is unavailable.

## REST administration API

Set the currently available quantity (zero is valid):

```http
PUT /api/inventory/{productId}
Content-Type: application/json

{
  "quantity": 10
}
```

Inspect inventory and its reservation history:

```http
GET /api/inventory/{productId}
```

`PUT` sets the available quantity directly and is intended for administration/development stock preparation. It does not rewrite reservation history. Reservation and release are deliberately exposed as application APIs rather than temporary production REST endpoints.

## Running

The default connection is `jdbc:postgresql://localhost:5432/inventory` with username and password `inventory`. Override it with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.

```shell
./gradlew test
./gradlew bootRun
```

## Future Kafka role

A future `adapter/in/kafka` can translate `ReserveInventoryCommand` messages to `ReserveInventoryUseCase` and call `ReleaseInventoryUseCase` for compensations. A future outbound adapter can publish `InventoryReservedEvent`, `InventoryRejectedEvent`, and `InventoryReleasedEvent`. No Kafka transport types or dependencies exist in the domain or application layers today.

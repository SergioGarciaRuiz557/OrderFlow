# OrderFlow

OrderFlow is a distributed backend project intended to demonstrate professional JVM backend engineering using microservices, Hexagonal Architecture, Domain-Driven Design, and event-driven communication.

Apache Kafka now provides JSON-based asynchronous communication between the four bounded contexts. The versioned contracts and topic topology are documented in [docs/messaging](docs/messaging/README.md). This transport increment intentionally does not claim Saga completion, Transactional Outbox, exactly-once delivery, generic consumer idempotency, retries, or Dead Letter Topics.

## Services

| Service | Language | Responsibility |
| --- | --- | --- |
| `order-service` | Java | Order lifecycle and future Saga orchestration |
| `inventory-service` | Kotlin | Inventory and stock reservation |
| `payment-service` | Kotlin | Payment authorization |
| `notification-service` | Kotlin | Order notifications |

All four bounded contexts include functional application behavior and Kafka adapters while remaining independently buildable and deployable.

## Architecture

Each service is an independent Spring Boot project organized around three areas:

- **Domain** contains business rules and is the innermost layer.
- **Application** defines ports and coordinates use cases.
- **Adapter** connects the application to external technologies and delivery mechanisms.

Dependencies flow inward from adapters to the application and then to the domain. See [docs/architecture.md](docs/architecture.md) for the package conventions.

## Build

Each service owns its Gradle build and wrapper. Run its tests independently from the service directory:

```shell
./gradlew clean test
```

## Continuous integration

GitHub Actions compiles and tests all four services after every push and on
every pull request targeting `main`. See
[the continuous integration guide](docs/continuous-integration.md) for the
workflow behavior and the required `main` branch protection settings.

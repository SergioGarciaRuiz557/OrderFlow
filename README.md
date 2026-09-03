# OrderFlow

OrderFlow is a distributed backend project intended to demonstrate professional JVM backend engineering using microservices, Hexagonal Architecture, Domain-Driven Design, and event-driven communication.

## Services

| Service | Language | Responsibility |
| --- | --- | --- |
| `order-service` | Java | Order lifecycle and future Saga orchestration |
| `inventory-service` | Kotlin | Inventory and stock reservation |
| `payment-service` | Kotlin | Payment authorization |
| `notification-service` | Kotlin | Order notifications |

The repository currently contains only the initial architectural bootstrap. Business logic and infrastructure will be introduced incrementally in future commits.

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


# OrderFlow Architecture

OrderFlow is organized as independent bounded contexts. Every microservice follows the same Hexagonal Architecture package convention while retaining its own build lifecycle and domain model.

## Dependency direction

Dependencies point inward:

```text
Adapter -> Application -> Domain
```

The domain does not depend on the application or adapters. The application does not depend on concrete adapter implementations.

## Package conventions

### `domain`

Contains business rules and concepts. Its subpackages are reserved for aggregates, entities and value objects (`model`), domain events (`event`), domain-specific failures (`exception`), and domain services (`service`). Domain services are appropriate only when behavior cannot naturally belong to an aggregate or value object.

### `application`

Coordinates use cases without infrastructure-specific behavior. `port.in` contains the input ports that expose use cases, `port.out` contains the output ports required by those use cases, and `service` contains future use-case implementations that coordinate ports and domain objects.

### `adapter`

Connects application ports to technologies and delivery mechanisms. Inbound adapters will live under `in.rest` and `in.kafka`. Outbound adapters will live under `out.persistence`, `out.kafka`, and `out.external`.

Hexagonal Architecture does not place business logic in adapters. Adapters translate between external concerns and application ports; business rules remain in the domain.

## Current state

Only package boundaries and Spring Boot entry points exist. No domain model, use cases, adapters, persistence, messaging, or HTTP endpoints have been implemented.


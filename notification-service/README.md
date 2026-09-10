# Notification Service

- **Bounded Context:** Notification
- **Language:** Kotlin
- **Architecture:** Hexagonal Architecture
- **Delivery:** Stateless fake email adapter

## Responsibility

Notification Service creates and delivers customer-facing messages for final order lifecycle events.
It currently supports deterministic order confirmation and order cancellation notifications.

The service has no REST API. Its intended inbound mechanism is asynchronous messaging, but it is
currently independent of Kafka.

## Architectural structure

- `domain/model`: the small provider-neutral notification model (`Notification`, `NotificationType`,
  `OrderId`, and validated email `Recipient`).
- `application/port/in`: `SendOrderConfirmedNotificationUseCase` and
  `SendOrderCancelledNotificationUseCase`, plus their immutable commands. These boundaries do not
  contain Kafka or other transport types.
- `application/service`: the corresponding orchestration services and deterministic
  `OrderNotificationFactory` for message content.
- `application/port/out`: `NotificationSender`, the delivery capability required by the application,
  and its notification-specific technical failure type.
- `adapter/out/external`: `FakeEmailNotificationSender`, which logs the recipient, notification type,
  order id, and message while requiring no provider account or external infrastructure.

Delivery exceptions are propagated. They are not translated into order cancellation or another
business outcome, allowing a future event consumer to apply an appropriate retry policy.

## Persistence decision

The initial service is deliberately stateless:

```text
application input port -> application service -> NotificationSender
```

There is no database, JPA, Flyway, or delivery-status model because the current requirement is to
send a notification, not to provide an audit history. Auditing can be added later if it becomes an
explicit business requirement.

## Future Kafka integration

Future adapters under `adapter/in/kafka` can consume `OrderConfirmedEvent` and
`OrderCancelledEvent`, map their payloads to the application commands, and invoke the corresponding
input ports. Listeners must remain thin; message construction and delivery orchestration stay in the
application layer.

Kafka is intentionally not included in the current build.

## Why the domain is intentionally simple

Domain-Driven Design is applied according to actual domain complexity. Order, Inventory, and Payment
need richer models because they enforce significant business invariants. Notification currently acts
primarily as an application and integration capability, so it does not justify aggregates, domain
services, persistence statuses, or policies with no behavior behind them.

The lightweight model still gives meaningful concepts explicit names and keeps application and
infrastructure boundaries clear. This is a deliberate design decision, not an architectural omission.

## Build and test

From this directory:

```shell
./gradlew test
```

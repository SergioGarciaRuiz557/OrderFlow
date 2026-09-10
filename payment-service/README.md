# Payment Service

`payment-service` owns the Payment bounded context. It is a Kotlin/Spring Boot service organized as
a hexagonal architecture: the domain contains payment rules, application ports describe use cases
and infrastructure needs, and outbound adapters implement PostgreSQL persistence and the current
deterministic gateway. There is intentionally no public REST API and no Kafka code in this version.

## Aggregate and lifecycle

`Payment` is the aggregate root. It owns the order, normalized EUR amount, payment method, lifecycle
outcome, timestamps, and optimistic-lock version. New payments begin `PENDING` and can make exactly
one domain transition:

```text
PENDING ── provider authorizes ──> AUTHORIZED
   │
   └──── provider declines ─────> REJECTED
```

Transitions are exposed as `authorize` and `reject`; status is never assigned by application or
adapter code. An authorized payment has one provider reference and no failure reason. A rejected
payment has one business failure reason and no provider reference. Pending payments have neither.
The same lifecycle rules are also database check constraints, protecting data written outside JPA.

Money uses `BigDecimal`, always carries a `Currency`, and is normalized to two decimal places without
rounding. Negative values and currencies other than EUR are rejected. Identifiers and references are
Kotlin value classes, aggregate snapshots are immutable data classes, and sealed interfaces make
gateway and use-case outcomes exhaustive.

## Authorization use case

`AuthorizePaymentUseCase` accepts an order id, amount, and payment method. Its service obtains an
order-scoped authorization lock, loads or creates the pending aggregate, invokes `PaymentGateway`,
applies the domain transition, and persists the result. `GetPaymentUseCase` retrieves a payment by
its payment id. These are deliberate business operations rather than generic CRUD endpoints.

`PaymentGateway` is an application output port. The adapter in `adapter/out/external` is deterministic:

| Payment method | Outcome |
|---|---|
| `pm-test-success` | Authorization with a stable fake provider reference |
| `pm-test-rejected` | Business rejection (`PAYMENT_METHOD_REJECTED`) |
| `pm-test-error` | `PaymentGatewayException`, representing a technical failure |
| any other value | Business rejection (`UNSUPPORTED_PAYMENT_METHOD`) |

A business rejection is a known provider decision and transitions the aggregate to `REJECTED`.
A timeout or other technical failure is not a rejection: the exception propagates and the payment
remains `PENDING`, so a future message consumer can retry it.

## Idempotency and persistence

Duplicate-charge protection exists independently of future message deduplication:

- `order_id` is the business payment-operation key and has a PostgreSQL unique constraint.
- A PostgreSQL advisory transaction lock serializes authorizations for the same order across service
  replicas. A technical gateway exception is rethrown only after the transaction commits the initial
  `PENDING` record, preserving retry state without weakening the external failure boundary.
- Existing `AUTHORIZED` and `REJECTED` payments return their original outcome without calling the
  gateway or writing again. A request with changed amount or method fails as a conflict.
- Every gateway request carries the stable `OrderId` business-operation key as its provider
  idempotency key. Retrying after an uncertain technical or persistence failure therefore reuses the
  same key, even if local state could not be committed.
- A JPA `@Version` column detects stale aggregate writes, while provider references are unique.

Flyway owns the `payments` schema. Hibernate only validates it. Local connection defaults are
`jdbc:postgresql://localhost:5432/payment` with user/password `payment`; `DB_URL`, `DB_USERNAME`, and
`DB_PASSWORD` override them.

## Future Kafka role

Kafka is deliberately deferred. A future inbound adapter will deserialize `AuthorizePaymentCommand`,
map transport values to the application command, and invoke `AuthorizePaymentUseCase`. Known business
declines will lead to `PaymentRejectedEvent`; successful decisions to `PaymentAuthorizedEvent`.
Technical gateway failures will escape the use case and remain eligible for consumer retry. Domain
and application packages will remain unaware of Kafka.

## Tests

Run from this directory:

```shell
./gradlew test
```

Domain and application tests use JUnit 5 and MockK without a Spring context. Persistence, retrieval,
Flyway, and database uniqueness are tested against PostgreSQL with Testcontainers; those tests are
skipped when Docker is unavailable. The fake gateway has standalone deterministic contract tests.

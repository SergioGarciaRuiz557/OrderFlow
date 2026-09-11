# Topic topology

| Topic | Producer | Consumer/group | Messages |
| --- | --- | --- | --- |
| `order.inventory.commands` | Order | Inventory / `inventory-service.commands` | `ReserveInventoryCommand`, `ReleaseInventoryCommand` |
| `inventory.order.events` | Inventory | Order / `order-service.inventory-events` | `InventoryReservedEvent`, `InventoryRejectedEvent`, `InventoryReleasedEvent` |
| `order.payment.commands` | Order | Payment / `payment-service.commands` | `AuthorizePaymentCommand` |
| `payment.order.events` | Payment | Order / `order-service.payment-events` | `PaymentAuthorizedEvent`, `PaymentRejectedEvent` |
| `order.events` | Order | Notification / `notification-service.order-events` | `OrderConfirmedEvent`, `OrderCancelledEvent` |

The topology groups compatible messages by producing and consuming bounded context. It keeps ownership obvious and topic count reasonable, allows additive compatible message types later, and avoids dozens of tiny topics without mixing unrelated concerns.

Topic names are configuration properties with environment overrides. Development beans create three-partition, single-replica topics; production infrastructure must provision them explicitly with environment-appropriate partition and replication settings.

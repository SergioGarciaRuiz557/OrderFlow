# Topología de topics

| Topic | Productor | Consumidor/grupo | Mensajes |
| --- | --- | --- | --- |
| `order.inventory.commands` | Order | Inventory / `inventory-service.commands` | `ReserveInventoryCommand`, `ReleaseInventoryCommand` |
| `inventory.order.events` | Inventory | Order / `order-service.inventory-events` | `InventoryReservedEvent`, `InventoryRejectedEvent`, `InventoryReleasedEvent` |
| `order.payment.commands` | Order | Payment / `payment-service.commands` | `AuthorizePaymentCommand` |
| `payment.order.events` | Payment | Order / `order-service.payment-events` | `PaymentAuthorizedEvent`, `PaymentRejectedEvent` |
| `order.events` | Order | Notification / `notification-service.order-events` | `OrderConfirmedEvent`, `OrderCancelledEvent` |

La topología agrupa mensajes compatibles por contexto delimitado productor y consumidor. Mantiene clara la propiedad y razonable la cantidad de topics, permite añadir posteriormente tipos de mensajes compatibles y evita decenas de topics diminutos sin mezclar responsabilidades no relacionadas.

Los nombres de los topics son propiedades de configuración que pueden sobrescribirse mediante variables de entorno. Los beans de desarrollo crean topics con tres particiones y una única réplica; la infraestructura de producción debe aprovisionarlos de forma explícita con una configuración de particiones y replicación adecuada para el entorno.

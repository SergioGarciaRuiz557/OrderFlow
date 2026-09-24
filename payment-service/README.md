# Servicio de pagos

`payment-service` es responsable del contexto delimitado de Pagos. Es un servicio Kotlin/Spring Boot organizado como una arquitectura hexagonal: el dominio contiene las reglas de pago, los puertos de aplicación describen los casos de uso y las necesidades de infraestructura, y los adaptadores de salida implementan la persistencia en PostgreSQL, la mensajería con Kafka y la pasarela determinista actual. Intencionadamente no existe una API REST pública.

## Agregado y ciclo de vida

`Payment` es la raíz del agregado. Posee el pedido, el importe normalizado en EUR, el método de pago, el resultado del ciclo de vida, las marcas de tiempo y la versión de bloqueo optimista. Los pagos nuevos comienzan en `PENDING` y pueden realizar exactamente una transición de dominio:

```text
PENDING ── el proveedor autoriza ──> AUTHORIZED
   │
   └──── el proveedor rechaza ─────> REJECTED
```

Las transiciones se exponen como `authorize` y `reject`; el código de aplicación o de los adaptadores nunca asigna el estado. Un pago autorizado tiene una referencia del proveedor y ningún motivo de fallo. Un pago rechazado tiene un motivo de fallo de negocio y ninguna referencia del proveedor. Los pagos pendientes no tienen ninguno de los dos. Las mismas reglas de ciclo de vida también son restricciones de comprobación de la base de datos, lo que protege los datos escritos fuera de JPA.

El dinero usa `BigDecimal`, siempre lleva una `Currency` y se normaliza a dos decimales sin redondear. Se rechazan los valores negativos y las monedas distintas de EUR. Los identificadores y las referencias son clases de valor de Kotlin, las instantáneas del agregado son clases de datos inmutables y las interfaces selladas hacen exhaustivos los resultados de la pasarela y de los casos de uso.

## Caso de uso de autorización

`AuthorizePaymentUseCase` acepta un identificador de pedido, un importe y un método de pago. Su servicio obtiene un bloqueo de autorización circunscrito al pedido, carga o crea el agregado pendiente, invoca `PaymentGateway`, aplica la transición del dominio y conserva el resultado. `GetPaymentUseCase` recupera un pago por su identificador. Son operaciones de negocio deliberadas, no endpoints CRUD genéricos.

`PaymentGateway` es un puerto de salida de la aplicación. El adaptador de `adapter/out/external` es determinista:

| Método de pago | Resultado |
|---|---|
| `pm-test-success` | Autorización con una referencia estable del proveedor simulado |
| `pm-test-rejected` | Rechazo de negocio (`PAYMENT_METHOD_REJECTED`) |
| `pm-test-error` | `PaymentGatewayException`, que representa un fallo técnico |
| cualquier otro valor | Rechazo de negocio (`UNSUPPORTED_PAYMENT_METHOD`) |

Un rechazo de negocio es una decisión conocida del proveedor y lleva al agregado a `REJECTED`. Un tiempo de espera agotado u otro fallo técnico no es un rechazo: la excepción se propaga y el pago permanece en `PENDING`, de modo que un futuro consumidor de mensajes pueda reintentarlo.

## Idempotencia y persistencia

La protección frente a cargos duplicados existe independientemente de la futura deduplicación de mensajes:

- `order_id` es la clave de la operación de pago de negocio y tiene una restricción única de PostgreSQL.
- Un bloqueo asesor transaccional de PostgreSQL serializa las autorizaciones del mismo pedido entre réplicas del servicio. Una excepción técnica de la pasarela solo se vuelve a lanzar después de que la transacción confirme el registro `PENDING` inicial, lo que conserva el estado de reintento sin debilitar el límite de fallos externos.
- Los pagos `AUTHORIZED` y `REJECTED` existentes devuelven su resultado original sin volver a llamar a la pasarela ni escribir. Una solicitud con un importe o método diferente falla por conflicto.
- Cada solicitud a la pasarela lleva la clave estable de operación de negocio `OrderId` como clave de idempotencia del proveedor. Por tanto, un reintento tras un fallo técnico o de persistencia incierto reutiliza la misma clave, aunque no se haya podido confirmar el estado local.
- Una columna JPA `@Version` detecta escrituras obsoletas del agregado, mientras que las referencias del proveedor son únicas.

Flyway es responsable del esquema `payments`. Hibernate solo lo valida. Los valores predeterminados de la conexión local son `jdbc:postgresql://localhost:5432/payment`, con usuario y contraseña `payment`; se pueden sustituir mediante `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.

## Integración con Kafka

El grupo `payment-service.commands` consume `AuthorizePaymentCommand` desde `order.payment.commands`. La aplicación invoca un puerto de salida semántico y el adaptador de Kafka publica `PaymentAuthorizedEvent` o `PaymentRejectedEvent` en `payment.order.events`. Los rechazos conocidos de la pasarela son eventos de negocio; los fallos técnicos de la pasarela se siguen propagando y no producen ningún evento de rechazo. Los registros de Pago contienen identificadores seguros y metadatos del sobre, nunca detalles de pago confidenciales.

## Pruebas

Ejecútalas desde este directorio:

```shell
./gradlew test
```

Las pruebas de dominio y aplicación usan JUnit 5 y MockK sin un contexto de Spring. La persistencia, la recuperación, Flyway y la unicidad de la base de datos se prueban contra PostgreSQL mediante Testcontainers; esas pruebas se omiten cuando Docker no está disponible. La pasarela simulada tiene pruebas de contrato deterministas independientes.

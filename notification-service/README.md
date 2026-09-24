# Servicio de notificaciones

Kafka es el mecanismo principal de integración de entrada. El grupo `notification-service.order-events` consume `OrderConfirmedEvent` y `OrderCancelledEvent` desde `order.events`; el consumidor valida y transforma cada sobre JSON e invoca el puerto de entrada existente para notificaciones de confirmación o cancelación. Notificaciones pone fin al flujo actual y, por tanto, no tiene productor de Kafka.

- **Contexto delimitado:** Notificaciones
- **Lenguaje:** Kotlin
- **Arquitectura:** arquitectura hexagonal
- **Entrega:** adaptador de correo electrónico simulado y sin estado

## Responsabilidad

El servicio de notificaciones crea y entrega mensajes destinados al cliente ante los eventos finales del ciclo de vida de un pedido. Actualmente admite notificaciones deterministas de confirmación y cancelación de pedidos.

El servicio no tiene una API REST. Su mecanismo de entrada es la mensajería asíncrona mediante Kafka.

## Estructura arquitectónica

- `domain/model`: el pequeño modelo de notificación independiente del proveedor (`Notification`,
  `NotificationType`, `OrderId` y el correo validado `Recipient`).
- `application/port/in`: `SendOrderConfirmedNotificationUseCase` y
  `SendOrderCancelledNotificationUseCase`, junto con sus comandos inmutables. Estos límites no
  contienen Kafka ni otros tipos de transporte.
- `application/service`: los servicios de orquestación correspondientes y
  `OrderNotificationFactory`, que genera de forma determinista el contenido del mensaje.
- `application/port/out`: `NotificationSender`, la capacidad de entrega que necesita la aplicación,
  y su tipo de fallo técnico específico de notificaciones.
- `adapter/out/external`: `FakeEmailNotificationSender`, que registra el destinatario, el tipo de
  notificación, el identificador del pedido y el mensaje sin necesitar una cuenta de proveedor ni
  infraestructura externa.

Las excepciones de entrega se propagan. No se convierten en una cancelación del pedido ni en otro
resultado de negocio, lo que permite que el consumidor de Kafka falle sin confirmar el registro.

## Decisión sobre persistencia

El servicio inicial carece de estado de forma deliberada:

```text
puerto de entrada de aplicación -> servicio de aplicación -> NotificationSender
```

No existen una base de datos, JPA, Flyway ni un modelo del estado de entrega porque el requisito
actual es enviar una notificación, no proporcionar un historial de auditoría. La auditoría podrá
añadirse más adelante si se convierte en un requisito de negocio explícito.

## Integración con Kafka

Los adaptadores de `adapter/in/kafka` consumen `OrderConfirmedEvent` y `OrderCancelledEvent`, transforman
sus cargas útiles en comandos de aplicación e invocan los puertos de entrada correspondientes. Los
consumidores se mantienen ligeros; la construcción del mensaje y la orquestación de la entrega
permanecen en la capa de aplicación. No existe un adaptador de salida de Kafka porque Notificaciones
finaliza actualmente el flujo de trabajo.

## Por qué el dominio es sencillo de forma intencionada

El diseño guiado por el dominio se aplica de acuerdo con la complejidad real del dominio. Pedidos,
Inventario y Pagos necesitan modelos más ricos porque aplican invariantes de negocio importantes.
Notificaciones actúa actualmente sobre todo como capacidad de aplicación e integración, por lo que
no justifica agregados, servicios de dominio, estados de persistencia ni políticas sin comportamiento.

El modelo ligero sigue dando nombres explícitos a conceptos relevantes y mantiene claros los
límites entre aplicación e infraestructura. Es una decisión de diseño deliberada, no una omisión
arquitectónica.

## Compilación y pruebas

Desde este directorio:

```shell
./gradlew test
```

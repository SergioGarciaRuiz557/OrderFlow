package com.orderflow.order.domain.model;

import com.orderflow.order.domain.event.InventoryRejected;
import com.orderflow.order.domain.event.InventoryReleaseRequested;
import com.orderflow.order.domain.event.InventoryReservationRequested;
import com.orderflow.order.domain.event.InventoryReserved;
import com.orderflow.order.domain.event.OrderCancelled;
import com.orderflow.order.domain.event.OrderConfirmed;
import com.orderflow.order.domain.event.OrderCreated;
import com.orderflow.order.domain.event.OrderDomainEvent;
import com.orderflow.order.domain.event.PaymentAuthorizationRequested;
import com.orderflow.order.domain.event.PaymentAuthorized;
import com.orderflow.order.domain.event.PaymentRejected;
import com.orderflow.order.domain.exception.DomainInvariantViolationException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Raíz del agregado que posee el límite completo de coherencia de negocio de un pedido.
 *
 * <p>Los consumidores no pueden asignar {@link OrderStatus} directamente. Solicitan comportamientos de negocio y esta
 * clase valida el estado actual, aplica la transición, actualiza el instante de modificación y
 * registra eventos de dominio. En esta primera versión del contexto delimitado, las líneas y el total
 * calculado son inmutables tras la creación.</p>
 *
 * <p>El agregado no contiene código de persistencia ni de mensajería. {@link #rehydrate} reconstruye el estado
 * guardado sin simular que las transiciones históricas acaban de ocurrir.</p>
 */
public final class Order {
    /** Identidad estable del agregado. */
    private final OrderId id;
    /** Cliente propietario del pedido. */
    private final CustomerId customerId;
    /** Instantánea inmutable de productos, cantidades y precios unitarios. */
    private final List<OrderLine> lines;
    /** Referencia de pago que se utilizará cuando la saga llegue a la autorización. */
    private final PaymentMethodId paymentMethodId;
    /** Suma de los subtotales de todas las líneas calculada por el dominio. */
    private final Money total;
    /** Instante en el que se creó originalmente el agregado. */
    private final Instant createdAt;
    /** Eventos producidos desde la última vez que la aplicación los extrajo para publicarlos. */
    private final List<OrderDomainEvent> domainEvents = new ArrayList<>();
    /** Estado actual del ciclo de vida; solo lo cambian los métodos de comportamiento de esta clase. */
    private OrderStatus status;
    /** Instante de la última transición de estado aceptada. */
    private Instant updatedAt;
    /** Versión de persistencia utilizada por el bloqueo optimista de JPA; es null antes de la primera inserción. */
    private Long version;

    /**
     * Constructor central compartido por la creación y la rehidratación tras sus validaciones específicas.
     * Copia la lista de líneas para que los consumidores externos no puedan modificar el contenido del agregado.
     */
    private Order(OrderId id, CustomerId customerId, List<OrderLine> lines,
                  PaymentMethodId paymentMethodId, OrderStatus status, Money total,
                  Instant createdAt, Instant updatedAt, Long version) {
        this.id = Objects.requireNonNull(id, "Order id is required");
        this.customerId = Objects.requireNonNull(customerId, "Customer id is required");
        if (lines == null || lines.isEmpty()) {
            throw new DomainInvariantViolationException("An order must contain at least one line");
        }
        this.lines = List.copyOf(lines);
        this.paymentMethodId = Objects.requireNonNull(paymentMethodId, "Payment method id is required");
        this.status = Objects.requireNonNull(status, "Order status is required");
        this.total = Objects.requireNonNull(total, "Order total is required");
        this.createdAt = Objects.requireNonNull(createdAt, "Creation time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Update time is required");
        this.version = version;
    }

    /**
     * Crea un agregado pendiente nuevo y registra esa creación como un hecho del dominio.
     *
     * <p>El total siempre se deriva de las líneas proporcionadas en lugar de aceptarlo de un consumidor.</p>
     *
     * @param id identidad nueva del agregado
     * @param customerId cliente que realiza el pedido
     * @param lines líneas de pedido no vacías
     * @param paymentMethodId referencia de pago para su posterior autorización
     * @param now instante de creación proporcionado mediante el puerto de reloj de la aplicación
     * @return agregado nuevo en {@link OrderStatus#PENDING}
     */
    public static Order create(OrderId id, CustomerId customerId, List<OrderLine> lines,
                               PaymentMethodId paymentMethodId, Instant now) {
        Money total = calculateTotal(lines);
        Order order = new Order(id, customerId, lines, paymentMethodId, OrderStatus.PENDING,
                total, now, now, null);
        order.domainEvents.add(new OrderCreated(id, now));
        return order;
    }

    /**
     * Reconstruye un agregado desde la persistencia sin registrar eventos de dominio nuevos.
     *
     * <p>La rehidratación vuelve a calcular el total de forma independiente y lo compara con el total persistido.
     * Una discrepancia indica que el estado almacenado está corrupto o es incompatible, por lo que se rechaza.</p>
     *
     * @param id identidad persistida del agregado
     * @param customerId identidad persistida del cliente
     * @param lines líneas persistidas
     * @param paymentMethodId referencia de pago persistida
     * @param status estado persistido del ciclo de vida
     * @param total total persistido, verificado con las líneas
     * @param createdAt instante de creación original
     * @param updatedAt instante de la última transición
     * @param version versión de bloqueo optimista
     * @return agregado completamente reconstruido sin eventos pendientes
     */
    public static Order rehydrate(OrderId id, CustomerId customerId, List<OrderLine> lines,
                                  PaymentMethodId paymentMethodId, OrderStatus status, Money total,
                                  Instant createdAt, Instant updatedAt, Long version) {
        Money calculatedTotal = calculateTotal(lines);
        if (!calculatedTotal.equals(total)) {
            throw new DomainInvariantViolationException("Persisted order total does not match its lines");
        }
        return new Order(id, customerId, lines, paymentMethodId, status, total, createdAt, updatedAt, version);
    }

    /**
     * Suma los subtotales de todas las líneas utilizando la divisa de la primera línea.
     *
     * @param lines líneas cuyo total se necesita
     * @return total inmutable calculado
     * @throws DomainInvariantViolationException cuando no se proporcionan líneas o las divisas son distintas
     */
    private static Money calculateTotal(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new DomainInvariantViolationException("An order must contain at least one line");
        }
        Money total = Money.zero(lines.getFirst().unitPrice().currency());
        for (OrderLine line : lines) {
            total = total.add(line.subtotal());
        }
        return total;
    }

    /**
     * Inicia la reserva de inventario para un pedido recién creado.
     *
     * <p>Una llamada duplicada mientras la reserva ya está pendiente no realiza ninguna operación porque representa
     * la misma intención de negocio. Cualquier otro estado inesperado se rechaza.</p>
     *
     * @param now instante de la transición
     */
    public void requestInventoryReservation(Instant now) {
        if (status == OrderStatus.INVENTORY_RESERVATION_PENDING) {
            return;
        }
        requireStatus(OrderStatus.PENDING, "Inventory reservation can only be requested for a pending order");
        transitionTo(OrderStatus.INVENTORY_RESERVATION_PENDING, now);
        domainEvents.add(new InventoryReservationRequested(id, now));
    }

    /**
     * Aplica una notificación correcta del inventario.
     *
     * <p>Los estados satisfactorios posteriores se aceptan como una reentrega idempotente. El método no solicita
     * el pago por sí mismo; esa orquestación pertenece al servicio de aplicación del resultado del inventario.</p>
     *
     * @param now instante de la transición
     */
    public void markInventoryReserved(Instant now) {
        if (status == OrderStatus.INVENTORY_RESERVED || status == OrderStatus.PAYMENT_PENDING || status == OrderStatus.CONFIRMED) {
            return;
        }
        requireStatus(OrderStatus.INVENTORY_RESERVATION_PENDING, "Inventory can only be reserved while reservation is pending");
        transitionTo(OrderStatus.INVENTORY_RESERVED, now);
        domainEvents.add(new InventoryReserved(id, now));
    }

    /**
     * Aplica una notificación fallida del inventario y finaliza el pedido como cancelado.
     *
     * @param reason motivo de negocio externo, normalizado a {@code unspecified} cuando está vacío
     * @param now instante de la transición
     */
    public void markInventoryRejected(String reason, Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        requireStatus(OrderStatus.INVENTORY_RESERVATION_PENDING, "Inventory can only be rejected while reservation is pending");
        transitionTo(OrderStatus.CANCELLED, now);
        domainEvents.add(new InventoryRejected(id, normalizedReason(reason), now));
        domainEvents.add(new OrderCancelled(id, normalizedReason(reason), now));
    }

    /**
     * Solicita el pago únicamente después de confirmar que el inventario está reservado.
     *
     * <p>Volver a llamar al método mientras el pago está pendiente es idempotente. Se rechaza expresamente llamarlo antes
     * de reservar el inventario para proteger la invariante de ordenación de la saga.</p>
     *
     * @param now instante de la transición
     */
    public void requestPaymentAuthorization(Instant now) {
        if (status == OrderStatus.PAYMENT_PENDING) {
            return;
        }
        requireStatus(OrderStatus.INVENTORY_RESERVED, "Payment cannot be authorized before inventory reservation");
        transitionTo(OrderStatus.PAYMENT_PENDING, now);
        domainEvents.add(new PaymentAuthorizationRequested(id, now));
    }

    /**
     * Aplica una autorización de pago correcta y confirma el pedido.
     *
     * <p>La autorización y la confirmación se emiten como hechos separados para que las integraciones puedan
     * reaccionar en el nivel semántico adecuado. Una reentrega posterior a la confirmación no realiza ninguna operación.</p>
     *
     * @param now instante de la transición
     */
    public void markPaymentAuthorized(Instant now) {
        if (status == OrderStatus.CONFIRMED) {
            return;
        }
        requireStatus(OrderStatus.PAYMENT_PENDING, "Payment can only be authorized while payment is pending");
        transitionTo(OrderStatus.CONFIRMED, now);
        domainEvents.add(new PaymentAuthorized(id, now));
        domainEvents.add(new OrderConfirmed(id, now));
    }

    /**
     * Aplica una autorización de pago fallida e inicia la compensación del inventario.
     *
     * <p>El pedido permanece en {@link OrderStatus#CANCELLATION_PENDING} hasta que otro flujo confirme
     * la compensación. Un evento {@link InventoryReleaseRequested} expresa la acción requerida.</p>
     *
     * @param reason motivo del rechazo del pago, normalizado cuando está vacío
     * @param now instante de la transición
     */
    public void markPaymentRejected(String reason, Instant now) {
        if (status == OrderStatus.CANCELLATION_PENDING || status == OrderStatus.CANCELLED) {
            return;
        }
        requireStatus(OrderStatus.PAYMENT_PENDING, "Payment can only be rejected while payment is pending");
        transitionTo(OrderStatus.CANCELLATION_PENDING, now);
        domainEvents.add(new PaymentRejected(id, normalizedReason(reason), now));
        domainEvents.add(new InventoryReleaseRequested(id, now));
    }

    /**
     * Solicita la cancelación antes de que se haya confirmado el pedido.
     *
     * <p>Si el inventario puede estar ya reservado, el método registra también una solicitud de compensación.
     * Los pedidos confirmados se rechazan deliberadamente porque cancelarlos requiere un proceso de negocio
     * posterior a la confirmación distinto.</p>
     *
     * @param reason contexto de la solicitud aportado por el consumidor; esta primera versión no lo persiste, por lo que el
     *               motivo definitivo debe proporcionarse de nuevo al confirmar la cancelación
     * @param now instante de la transición
     */
    public void requestCancellation(String reason, Instant now) {
        if (status == OrderStatus.CANCELLATION_PENDING || status == OrderStatus.CANCELLED) {
            return;
        }
        if (status == OrderStatus.CONFIRMED) {
            throw new DomainInvariantViolationException("A confirmed order cannot be cancelled through this transition");
        }
        boolean inventoryWasReserved = status == OrderStatus.INVENTORY_RESERVED || status == OrderStatus.PAYMENT_PENDING;
        transitionTo(OrderStatus.CANCELLATION_PENDING, now);
        if (inventoryWasReserved) {
            domainEvents.add(new InventoryReleaseRequested(id, now));
        }
    }

    /**
     * Completa una cancelación solicitada previamente.
     *
     * @param reason motivo final de la cancelación, normalizado cuando está vacío
     * @param now instante de la transición
     */
    public void confirmCancellation(String reason, Instant now) {
        if (status == OrderStatus.CANCELLED) {
            return;
        }
        requireStatus(OrderStatus.CANCELLATION_PENDING, "Cancellation can only be confirmed while cancellation is pending");
        transitionTo(OrderStatus.CANCELLED, now);
        domainEvents.add(new OrderCancelled(id, normalizedReason(reason), now));
    }

    /**
     * Protege una transición que solo es válida desde un estado concreto.
     *
     * @param expected estado actual requerido
     * @param message explicación de negocio utilizada si falla la protección
     */
    private void requireStatus(OrderStatus expected, String message) {
        if (status != expected) {
            throw new DomainInvariantViolationException(message + "; current status is " + status);
        }
    }

    /** Asigna el siguiente estado validado y el instante de su transición en una sola operación interna. */
    private void transitionTo(OrderStatus newStatus, Instant now) {
        status = newStatus;
        updatedAt = Objects.requireNonNull(now, "Transition time is required");
    }

    /** Convierte los motivos externos ausentes o vacíos en un valor estable del dominio. */
    private String normalizedReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.trim();
    }

    /**
     * Devuelve todos los eventos producidos desde la extracción anterior y limpia el búfer interno.
     *
     * <p>La limpieza evita que la misma instancia del agregado publique un evento dos veces. Los servicios
     * de aplicación solo llaman a este método después de que la persistencia finalice correctamente.</p>
     *
     * @return instantánea inmutable de los eventos pendientes actuales
     */
    public List<OrderDomainEvent> pullDomainEvents() {
        List<OrderDomainEvent> events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    /**
     * Devuelve la identidad de este agregado.
     *
     * @return identidad del agregado
     */
    public OrderId id() { return id; }
    /**
     * Devuelve el cliente propietario.
     *
     * @return cliente propietario del pedido
     */
    public CustomerId customerId() { return customerId; }
    /**
     * Devuelve las condiciones inmutables de la compra.
     *
     * @return instantánea inmutable de las líneas del pedido
     */
    public List<OrderLine> lines() { return lines; }
    /**
     * Devuelve la referencia para la autorización posterior.
     *
     * @return referencia del método de pago
     */
    public PaymentMethodId paymentMethodId() { return paymentMethodId; }
    /**
     * Devuelve el estado controlado por el dominio.
     *
     * @return estado actual del ciclo de vida
     */
    public OrderStatus status() { return status; }
    /**
     * Devuelve la suma calculada a partir de todas las líneas.
     *
     * @return total calculado por el dominio
     */
    public Money total() { return total; }
    /**
     * Devuelve cuándo se inició el agregado.
     *
     * @return instante de creación
     */
    public Instant createdAt() { return createdAt; }
    /**
     * Devuelve cuándo cambió el estado por última vez.
     *
     * @return instante de la última transición
     */
    public Instant updatedAt() { return updatedAt; }
    /**
     * Devuelve el token de concurrencia.
     *
     * @return versión de persistencia, o null antes del primer guardado
     */
    public Long version() { return version; }
}

package com.orderflow.order.application.port.out;

import java.time.Instant;

/** Supplies business time without coupling application services to the system clock. */
public interface ClockProvider {
    /**
     * Obtains the current business time.
     *
     * @return current instant for aggregate creation or transition timestamps
     */
    Instant now();
}

package com.orderflow.inventory.application.port.`out`

import java.time.Instant

fun interface ClockProvider {
    fun now(): Instant
}

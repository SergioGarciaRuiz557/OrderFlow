package com.orderflow.notification.application.service

import com.orderflow.notification.domain.model.Notification
import com.orderflow.notification.domain.model.NotificationType
import com.orderflow.notification.domain.model.OrderId
import com.orderflow.notification.domain.model.Recipient
import org.springframework.stereotype.Component

/** Builds the deterministic notification content currently required by the application. */
@Component
class OrderNotificationFactory {
    fun confirmed(orderId: OrderId, recipient: Recipient): Notification = Notification(
        orderId = orderId,
        recipient = recipient,
        type = NotificationType.ORDER_CONFIRMED,
        message = "Your order ${orderId.value} has been confirmed.",
    )

    fun cancelled(orderId: OrderId, recipient: Recipient): Notification = Notification(
        orderId = orderId,
        recipient = recipient,
        type = NotificationType.ORDER_CANCELLED,
        message = "Your order ${orderId.value} has been cancelled.",
    )
}

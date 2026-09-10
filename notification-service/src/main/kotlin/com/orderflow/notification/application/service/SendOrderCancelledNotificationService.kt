package com.orderflow.notification.application.service

import com.orderflow.notification.application.port.`in`.SendOrderCancelledNotificationCommand
import com.orderflow.notification.application.port.`in`.SendOrderCancelledNotificationUseCase
import com.orderflow.notification.application.port.`out`.NotificationSender
import org.springframework.stereotype.Service

/** Application orchestration for the order-cancelled notification flow. */
@Service
class SendOrderCancelledNotificationService(
    private val notificationFactory: OrderNotificationFactory,
    private val notificationSender: NotificationSender,
) : SendOrderCancelledNotificationUseCase {
    override fun send(command: SendOrderCancelledNotificationCommand) {
        val notification = notificationFactory.cancelled(command.orderId, command.recipient)
        notificationSender.send(notification)
    }
}

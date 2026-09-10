package com.orderflow.notification.application.service

import com.orderflow.notification.application.port.`in`.SendOrderConfirmedNotificationCommand
import com.orderflow.notification.application.port.`in`.SendOrderConfirmedNotificationUseCase
import com.orderflow.notification.application.port.`out`.NotificationSender
import org.springframework.stereotype.Service

/** Application orchestration for the order-confirmed notification flow. */
@Service
class SendOrderConfirmedNotificationService(
    private val notificationFactory: OrderNotificationFactory,
    private val notificationSender: NotificationSender,
) : SendOrderConfirmedNotificationUseCase {
    override fun send(command: SendOrderConfirmedNotificationCommand) {
        val notification = notificationFactory.confirmed(command.orderId, command.recipient)
        notificationSender.send(notification)
    }
}

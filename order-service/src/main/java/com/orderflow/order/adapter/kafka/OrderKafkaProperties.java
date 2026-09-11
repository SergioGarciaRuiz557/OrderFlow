package com.orderflow.order.adapter.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orderflow.kafka")
public record OrderKafkaProperties(Topics topics) {
    public record Topics(String inventoryCommands, String inventoryEvents, String paymentCommands,
                         String paymentEvents, String orderEvents) { }
}

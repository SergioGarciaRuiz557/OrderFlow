package com.orderflow.notification.adapter.`in`.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

@ConfigurationProperties("orderflow.kafka")
data class NotificationKafkaProperties(val topics: Topics) {
    data class Topics(val orderEvents: String)
}

@Configuration
@ConditionalOnProperty(name = ["orderflow.kafka.enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(NotificationKafkaProperties::class)
class NotificationKafkaConfiguration {
    @Bean fun consumerFactory(boot: KafkaProperties): ConsumerFactory<String, String> {
        val props = boot.buildConsumerProperties().toMutableMap()
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        return DefaultKafkaConsumerFactory(props, StringDeserializer(), StringDeserializer())
    }
    @Bean fun kafkaListenerContainerFactory(factory: ConsumerFactory<String, String>) =
        ConcurrentKafkaListenerContainerFactory<String, String>().also {
            it.consumerFactory = factory
            it.containerProperties.ackMode = ContainerProperties.AckMode.RECORD
        }
    @Bean fun orderEventsTopic(p: NotificationKafkaProperties) = NewTopic(p.topics.orderEvents, 3, 1.toShort())
}

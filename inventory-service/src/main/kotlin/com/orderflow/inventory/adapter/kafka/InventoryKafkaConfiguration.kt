package com.orderflow.inventory.adapter.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.ContainerProperties
import org.apache.kafka.clients.admin.NewTopic

@ConfigurationProperties("orderflow.kafka")
data class InventoryKafkaProperties(val topics: Topics) {
    data class Topics(val inventoryCommands: String, val inventoryEvents: String)
}

@Configuration
@ConditionalOnProperty(name = ["orderflow.kafka.enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(InventoryKafkaProperties::class)
class InventoryKafkaConfiguration {
    @Bean fun producerFactory(boot: KafkaProperties): ProducerFactory<String, String> {
        val props = boot.buildProducerProperties().toMutableMap()
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.ACKS_CONFIG] = "all"
        return DefaultKafkaProducerFactory(props)
    }
    @Bean fun kafkaTemplate(factory: ProducerFactory<String, String>) = KafkaTemplate(factory)
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
    @Bean fun inventoryCommandsTopic(p: InventoryKafkaProperties) = NewTopic(p.topics.inventoryCommands, 3, 1.toShort())
    @Bean fun inventoryEventsTopic(p: InventoryKafkaProperties) = NewTopic(p.topics.inventoryEvents, 3, 1.toShort())
}

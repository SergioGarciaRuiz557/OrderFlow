package com.orderflow.order.adapter.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(name = "orderflow.kafka.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OrderKafkaProperties.class)
public class OrderKafkaConfiguration {
    @Bean ProducerFactory<String, String> producerFactory(org.springframework.boot.autoconfigure.kafka.KafkaProperties boot) {
        Map<String, Object> properties = new HashMap<>(boot.buildProducerProperties());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> factory) {
        return new KafkaTemplate<>(factory);
    }

    @Bean ConsumerFactory<String, String> consumerFactory(org.springframework.boot.autoconfigure.kafka.KafkaProperties boot) {
        Map<String, Object> properties = new HashMap<>(boot.buildConsumerProperties());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), new StringDeserializer());
    }

    @Bean ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(ConsumerFactory<String, String> factory) {
        var result = new ConcurrentKafkaListenerContainerFactory<String, String>();
        result.setConsumerFactory(factory);
        result.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return result;
    }

    @Bean NewTopic inventoryCommandsTopic(OrderKafkaProperties p) { return topic(p.topics().inventoryCommands()); }
    @Bean NewTopic inventoryEventsTopic(OrderKafkaProperties p) { return topic(p.topics().inventoryEvents()); }
    @Bean NewTopic paymentCommandsTopic(OrderKafkaProperties p) { return topic(p.topics().paymentCommands()); }
    @Bean NewTopic paymentEventsTopic(OrderKafkaProperties p) { return topic(p.topics().paymentEvents()); }
    @Bean NewTopic orderEventsTopic(OrderKafkaProperties p) { return topic(p.topics().orderEvents()); }
    private NewTopic topic(String name) { return new NewTopic(name, 3, (short) 1); }
}

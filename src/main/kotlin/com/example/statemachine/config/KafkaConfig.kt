package com.example.statemachine.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {
    @Bean
    fun paymentConfirmedTopic(): NewTopic {
        return TopicBuilder.name("payment.confirmed")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun orderShippedTopic(): NewTopic {
        return TopicBuilder.name("order.shipped")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun orderDeliveredTopic(): NewTopic {
        return TopicBuilder.name("order.delivered")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun orderRefundedTopic(): NewTopic {
        return TopicBuilder.name("order.refunded")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun orderEventsTopic(): NewTopic {
        return TopicBuilder.name("order.events")
            .partitions(3)
            .replicas(1)
            .build()
    }
}

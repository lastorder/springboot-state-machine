package com.example.statemachine.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {
    // Inventory topics
    @Bean
    fun inventoryCheckRequestTopic(): NewTopic {
        return TopicBuilder.name("inventory.check.request")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun inventoryCheckResponseTopic(): NewTopic {
        return TopicBuilder.name("inventory.check.response")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun inventoryOrderModifiedTopic(): NewTopic {
        return TopicBuilder.name("inventory.order.modified")
            .partitions(3)
            .replicas(1)
            .build()
    }

    // Pricing topics
    @Bean
    fun pricingRequestTopic(): NewTopic {
        return TopicBuilder.name("pricing.request")
            .partitions(3)
            .replicas(1)
            .build()
    }

    @Bean
    fun pricingResponseTopic(): NewTopic {
        return TopicBuilder.name("pricing.response")
            .partitions(3)
            .replicas(1)
            .build()
    }

    // Payment topic
    @Bean
    fun paymentConfirmedTopic(): NewTopic {
        return TopicBuilder.name("payment.confirmed")
            .partitions(3)
            .replicas(1)
            .build()
    }

    // Order lifecycle topics
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

    // Order events topic
    @Bean
    fun orderEventsTopic(): NewTopic {
        return TopicBuilder.name("order.events")
            .partitions(3)
            .replicas(1)
            .build()
    }
}

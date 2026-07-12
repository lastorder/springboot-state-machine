package com.example.statemachine.infrastructure.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {
    @Bean
    fun inventoryCheckRequestTopic(): NewTopic =
        TopicBuilder
            .name("inventory.check.request")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun inventoryCheckResponseTopic(): NewTopic =
        TopicBuilder
            .name("inventory.check.response")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun inventoryOrderModifiedTopic(): NewTopic =
        TopicBuilder
            .name("inventory.order.modified")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun pricingRequestTopic(): NewTopic =
        TopicBuilder
            .name("pricing.request")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun pricingResponseTopic(): NewTopic =
        TopicBuilder
            .name("pricing.response")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun paymentConfirmedTopic(): NewTopic =
        TopicBuilder
            .name("payment.confirmed")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun orderShippedTopic(): NewTopic =
        TopicBuilder
            .name("order.shipped")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun orderDeliveredTopic(): NewTopic =
        TopicBuilder
            .name("order.delivered")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun orderRefundedTopic(): NewTopic =
        TopicBuilder
            .name("order.refunded")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun orderEventsTopic(): NewTopic =
        TopicBuilder
            .name("order.events")
            .partitions(3)
            .replicas(1)
            .build()
}

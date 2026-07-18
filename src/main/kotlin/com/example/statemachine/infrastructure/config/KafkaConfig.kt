package com.example.statemachine.infrastructure.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

@Configuration
class KafkaConfig {
    @Bean
    fun orderEventsTopic(): NewTopic =
        TopicBuilder
            .name("order.events")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun prApprovedTopic(): NewTopic =
        TopicBuilder
            .name("pr.approved")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun coeOrderCreatedTopic(): NewTopic =
        TopicBuilder
            .name("coe.order.created")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun factoryVomTopic(): NewTopic =
        TopicBuilder
            .name("factory.vom")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun factoryDomTopic(): NewTopic =
        TopicBuilder
            .name("factory.dom")
            .partitions(3)
            .replicas(1)
            .build()

    @Bean
    fun factoryVomFailedTopic(): NewTopic =
        TopicBuilder
            .name("factory.vom.failed")
            .partitions(3)
            .replicas(1)
            .build()
}

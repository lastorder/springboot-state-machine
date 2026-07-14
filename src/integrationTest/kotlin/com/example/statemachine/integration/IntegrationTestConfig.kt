package com.example.statemachine.integration

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration
class IntegrationTestConfig {
    @Bean
    @ServiceConnection
    fun kafkaContainer(): ConfluentKafkaContainer = ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
}

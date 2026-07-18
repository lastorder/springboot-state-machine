package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.infrastructure.kafka.dto.OrderStatusChangeEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class OrderEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendStatusChangeEvent(event: OrderStatusChangeEvent) {
        log.info("Sending order status change event: $event")
        kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, event.orderId.toString(), event)
    }

    fun sendStatusChangeEventAsync(
        event: OrderStatusChangeEvent,
    ): CompletableFuture<org.springframework.kafka.support.SendResult<String, Any>> {
        log.info("Sending order status change event async: $event")
        return kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, event.orderId.toString(), event)
    }
}

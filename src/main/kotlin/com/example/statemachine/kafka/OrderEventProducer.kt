package com.example.statemachine.kafka

import com.example.statemachine.kafka.dto.OrderStatusChangeEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class OrderEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val ORDER_EVENTS_TOPIC = "order.events"
        const val ORDER_NOTIFICATIONS_TOPIC = "order.notifications"
    }

    fun sendStatusChangeEvent(event: OrderStatusChangeEvent) {
        log.info("Sending order status change event: $event")
        kafkaTemplate.send(ORDER_EVENTS_TOPIC, event.orderId.toString(), event)
    }

    fun sendStatusChangeEventAsync(
        event: OrderStatusChangeEvent,
    ): CompletableFuture<org.springframework.kafka.support.SendResult<String, Any>> {
        log.info("Sending order status change event async: $event")
        return kafkaTemplate.send(ORDER_EVENTS_TOPIC, event.orderId.toString(), event)
    }
}

package com.example.statemachine.kafka

import com.example.statemachine.kafka.dto.InventoryCheckRequest
import com.example.statemachine.kafka.dto.OrderStatusChangeEvent
import com.example.statemachine.kafka.dto.PricingRequest
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
        const val INVENTORY_CHECK_REQUEST_TOPIC = "inventory.check.request"
        const val PRICING_REQUEST_TOPIC = "pricing.request"
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

    fun sendInventoryCheckRequest(request: InventoryCheckRequest) {
        log.info("Sending inventory check request: $request")
        kafkaTemplate.send(INVENTORY_CHECK_REQUEST_TOPIC, request.orderId.toString(), request)
    }

    fun sendPricingRequest(request: PricingRequest) {
        log.info("Sending pricing request: $request")
        kafkaTemplate.send(PRICING_REQUEST_TOPIC, request.orderId.toString(), request)
    }
}

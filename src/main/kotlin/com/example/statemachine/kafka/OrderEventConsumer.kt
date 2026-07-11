package com.example.statemachine.kafka

import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.kafka.dto.OrderDeliveredEvent
import com.example.statemachine.kafka.dto.OrderRefundedEvent
import com.example.statemachine.kafka.dto.OrderShippedEvent
import com.example.statemachine.kafka.dto.PaymentConfirmedEvent
import com.example.statemachine.service.StateMachineService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer(
    private val stateMachineService: StateMachineService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["payment.confirmed"], groupId = "order-state-machine-group")
    fun onPaymentConfirmed(record: ConsumerRecord<String, PaymentConfirmedEvent>) {
        val event = record.value()
        log.info("Received payment confirmed event: orderId=${event.orderId}")
        stateMachineService.sendEvent(event.orderId, OrderEvent.CONFIRM_PAYMENT)
    }

    @KafkaListener(topics = ["order.shipped"], groupId = "order-state-machine-group")
    fun onOrderShipped(record: ConsumerRecord<String, OrderShippedEvent>) {
        val event = record.value()
        log.info("Received order shipped event: orderId=${event.orderId}")
        stateMachineService.sendEvent(event.orderId, OrderEvent.SHIP)
    }

    @KafkaListener(topics = ["order.delivered"], groupId = "order-state-machine-group")
    fun onOrderDelivered(record: ConsumerRecord<String, OrderDeliveredEvent>) {
        val event = record.value()
        log.info("Received order delivered event: orderId=${event.orderId}")
        stateMachineService.sendEvent(event.orderId, OrderEvent.DELIVER)
    }

    @KafkaListener(topics = ["order.refunded"], groupId = "order-state-machine-group")
    fun onOrderRefunded(record: ConsumerRecord<String, OrderRefundedEvent>) {
        val event = record.value()
        log.info("Received order refunded event: orderId=${event.orderId}")
        stateMachineService.sendEvent(event.orderId, OrderEvent.REFUND)
    }
}

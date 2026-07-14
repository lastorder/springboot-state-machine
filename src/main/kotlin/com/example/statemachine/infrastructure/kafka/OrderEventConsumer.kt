package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.kafka.dto.DomEvent
import com.example.statemachine.infrastructure.kafka.dto.PrApprovedEvent
import com.example.statemachine.infrastructure.kafka.dto.VomEvent
import com.example.statemachine.order.service.OrderCommandService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer(
    private val orderCommandService: OrderCommandService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["pr.approved"], groupId = "order-state-machine-group")
    fun onPrApproved(record: ConsumerRecord<String, PrApprovedEvent>) {
        val event = record.value()
        log.info("Received PR_APPROVED event: orderNo=${event.orderNo}")

        orderCommandService.submitOrderEvent(
            orderId = event.orderId,
            event = OrderEvent.PR_APPROVED,
            headers =
                mapOf(
                    "orderNo" to event.orderNo,
                    "productId" to event.productId,
                    "productName" to event.productName,
                    "quantity" to event.quantity,
                    "amount" to event.amount,
                ),
        )
    }

    @KafkaListener(topics = ["factory.vom"], groupId = "order-state-machine-group")
    fun onVom(record: ConsumerRecord<String, VomEvent>) {
        val event = record.value()
        log.info("Received VOM event: orderId=${event.orderId}")

        orderCommandService.submitOrderEvent(
            orderId = event.orderId,
            event = OrderEvent.VOM,
        )
    }

    @KafkaListener(topics = ["factory.dom"], groupId = "order-state-machine-group")
    fun onDom(record: ConsumerRecord<String, DomEvent>) {
        val event = record.value()
        log.info("Received DOM event: orderId=${event.orderId}")

        orderCommandService.submitOrderEvent(
            orderId = event.orderId,
            event = OrderEvent.DOM,
        )
    }

    @KafkaListener(topics = ["factory.vom.failed"], groupId = "order-state-machine-group")
    fun onVomFailed(record: ConsumerRecord<String, VomEvent>) {
        val event = record.value()
        log.info("Received VOM_FAILED event: orderId=${event.orderId}")

        orderCommandService.submitOrderEvent(
            orderId = event.orderId,
            event = OrderEvent.VOM_FAILED,
        )
    }
}

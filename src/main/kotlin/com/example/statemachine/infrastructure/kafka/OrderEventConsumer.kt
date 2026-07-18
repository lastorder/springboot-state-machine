package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.application.barrier.OrderInitBarrier
import com.example.statemachine.application.barrier.OrderInitBarrierAggregate
import com.example.statemachine.application.service.OrderCommandService
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.kafka.dto.DomEvent
import com.example.statemachine.infrastructure.kafka.dto.PrApprovedEvent
import com.example.statemachine.infrastructure.kafka.dto.VomEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer(
    private val orderCommandService: OrderCommandService,
    private val orderInitBarrierAggregate: OrderInitBarrierAggregate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaTopics.PR_APPROVED],
        properties = ["spring.json.value.default.type=com.example.statemachine.infrastructure.kafka.dto.PrApprovedEvent"],
    )
    fun onPrApproved(record: ConsumerRecord<String, PrApprovedEvent>) {
        val event = record.value()
        log.info("Received PR_APPROVED event: orderNo=${event.orderNo}")

        orderCommandService.submitOrderEvent(
            orderNo = event.orderNo,
            event = OrderEvent.PR_APPROVED,
            headers =
                mapOf<String, Any?>(
                    "orderNo" to event.orderNo,
                    "productId" to event.productId,
                    "productName" to event.productName,
                    "quantity" to event.quantity,
                    "amount" to event.amount,
                    "market" to event.market.name,
                ),
        )
    }

    @KafkaListener(
        topics = [KafkaTopics.FACTORY_VOM],
        properties = ["spring.json.value.default.type=com.example.statemachine.infrastructure.kafka.dto.VomEvent"],
    )
    fun onVom(record: ConsumerRecord<String, VomEvent>) {
        val event = record.value()
        log.info("Received VOM event: orderNo=${event.orderNo}")

        orderInitBarrierAggregate.handleBarrierEvent(event.orderNo, OrderInitBarrier.VOM)
    }

    @KafkaListener(
        topics = [KafkaTopics.FACTORY_DOM],
        properties = ["spring.json.value.default.type=com.example.statemachine.infrastructure.kafka.dto.DomEvent"],
    )
    fun onDom(record: ConsumerRecord<String, DomEvent>) {
        val event = record.value()
        log.info("Received DOM event: orderNo=${event.orderNo}")

        orderInitBarrierAggregate.handleBarrierEvent(event.orderNo, OrderInitBarrier.DOM)
    }

    @KafkaListener(
        topics = [KafkaTopics.FACTORY_VOM_FAILED],
        properties = ["spring.json.value.default.type=com.example.statemachine.infrastructure.kafka.dto.VomEvent"],
    )
    fun onVomFailed(record: ConsumerRecord<String, VomEvent>) {
        val event = record.value()
        log.info("Received VOM_FAILED event: orderNo=${event.orderNo}")

        orderCommandService.submitOrderEvent(
            orderNo = event.orderNo,
            event = OrderEvent.VOM_FAILED,
        )
    }
}

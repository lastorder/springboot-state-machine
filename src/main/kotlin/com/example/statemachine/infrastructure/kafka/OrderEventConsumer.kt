package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.kafka.dto.DomEvent
import com.example.statemachine.infrastructure.kafka.dto.PrApprovedEvent
import com.example.statemachine.infrastructure.kafka.dto.VomEvent
import com.example.statemachine.order.barrier.OrderInitBarrier
import com.example.statemachine.order.barrier.OrderInitBarrierAggregate
import com.example.statemachine.statemachine.service.StateMachineService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class OrderEventConsumer(
    private val stateMachineService: StateMachineService,
    private val orderInitBarrierAggregate: OrderInitBarrierAggregate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["pr.approved"],
        properties = ["spring.json.value.default.type=com.example.statemachine.infrastructure.kafka.dto.PrApprovedEvent"],
    )
    fun onPrApproved(record: ConsumerRecord<String, PrApprovedEvent>) {
        val event = record.value()
        log.info("Received PR_APPROVED event: orderNo=${event.orderNo}")

        stateMachineService.sendEvent(
            orderNo = event.orderNo,
            event = OrderEvent.PR_APPROVED,
            headers =
                mapOf<String, Any>(
                    "orderNo" to event.orderNo,
                    "productId" to (event.productId ?: ""),
                    "productName" to (event.productName ?: ""),
                    "quantity" to (event.quantity ?: 0),
                    "amount" to (event.amount ?: BigDecimal.ZERO),
                ),
        )
    }

    @KafkaListener(
        topics = ["factory.vom"],
        properties = ["spring.json.value.default.type=com.example.statemachine.infrastructure.kafka.dto.VomEvent"],
    )
    fun onVom(record: ConsumerRecord<String, VomEvent>) {
        val event = record.value()
        log.info("Received VOM event: orderNo=${event.orderNo}")

        orderInitBarrierAggregate.handleBarrierEvent(event.orderNo, OrderInitBarrier.VOM)
    }

    @KafkaListener(
        topics = ["factory.dom"],
        properties = ["spring.json.value.default.type=com.example.statemachine.infrastructure.kafka.dto.DomEvent"],
    )
    fun onDom(record: ConsumerRecord<String, DomEvent>) {
        val event = record.value()
        log.info("Received DOM event: orderNo=${event.orderNo}")

        orderInitBarrierAggregate.handleBarrierEvent(event.orderNo, OrderInitBarrier.DOM)
    }

    @KafkaListener(
        topics = ["factory.vom.failed"],
        properties = ["spring.json.value.default.type=com.example.statemachine.infrastructure.kafka.dto.VomEvent"],
    )
    fun onVomFailed(record: ConsumerRecord<String, VomEvent>) {
        val event = record.value()
        log.info("Received VOM_FAILED event: orderNo=${event.orderNo}")

        stateMachineService.sendEvent(
            orderNo = event.orderNo,
            event = OrderEvent.VOM_FAILED,
        )
    }
}

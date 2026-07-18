package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.infrastructure.kafka.dto.BarrierPassEvent
import com.example.statemachine.infrastructure.kafka.dto.ChangeTriggerEvent
import com.example.statemachine.order.barrier.CdoaAcceptBarrier
import com.example.statemachine.order.barrier.CdoaAcceptBarrierAggregate
import com.example.statemachine.order.barrier.PurchaseRequestAcceptBarrier
import com.example.statemachine.order.barrier.PurchaseRequestAcceptBarrierAggregate
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ChangeTriggerConsumer(
    private val purchaseRequestAcceptBarrierAggregate: PurchaseRequestAcceptBarrierAggregate,
    private val cdoaAcceptBarrierAggregate: CdoaAcceptBarrierAggregate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaTopics.CHANGE_TRIGGER],
        properties = ["spring.json.value.default.type=com.example.statemachine.infrastructure.kafka.dto.ChangeTriggerEvent"],
    )
    fun onChangeTrigger(record: ConsumerRecord<String, ChangeTriggerEvent>) {
        val event = record.value()
        log.info("Received CHANGE_TRIGGER event: orderNo=${event.orderNo}, market=${event.market}, eventType=${event.eventType}")
    }

    @KafkaListener(
        topics = [KafkaTopics.BARRIER_PASS],
        properties = ["spring.json.value.default.type=com.example.statemachine.infrastructure.kafka.dto.BarrierPassEvent"],
    )
    fun onBarrierPass(record: ConsumerRecord<String, BarrierPassEvent>) {
        val event = record.value()
        log.info(
            "Received BARRIER_PASS event: orderNo=${event.orderNo}, barrierType=${event.barrierType}, " +
                "flowType=${event.flowType}, success=${event.success}",
        )

        if (!event.success) {
            log.warn("Barrier pass event indicates failure, skipping: orderNo=${event.orderNo}")
            return
        }

        when (event.flowType) {
            PurchaseRequestAcceptBarrier.FLOW_TYPE ->
                purchaseRequestAcceptBarrierAggregate.handleBarrierEvent(event.orderNo, event.barrierType)
            CdoaAcceptBarrier.FLOW_TYPE ->
                cdoaAcceptBarrierAggregate.handleBarrierEvent(event.orderNo, event.barrierType)
            else -> log.warn("Unknown flowType: ${event.flowType}")
        }
    }
}

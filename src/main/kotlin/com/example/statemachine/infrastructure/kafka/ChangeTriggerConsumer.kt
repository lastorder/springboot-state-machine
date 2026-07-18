package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.application.barrier.CdoaAcceptBarrier
import com.example.statemachine.application.barrier.CdoaAcceptBarrierAggregate
import com.example.statemachine.application.barrier.PurchaseRequestAcceptBarrier
import com.example.statemachine.application.barrier.PurchaseRequestAcceptBarrierAggregate
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.kafka.dto.BarrierPassEvent
import com.example.statemachine.infrastructure.kafka.dto.ChangeTriggerEvent
import com.example.statemachine.statemachine.service.StateMachineService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class ChangeTriggerConsumer(
    private val purchaseRequestAcceptBarrierAggregate: PurchaseRequestAcceptBarrierAggregate,
    private val cdoaAcceptBarrierAggregate: CdoaAcceptBarrierAggregate,
    private val stateMachineService: StateMachineService,
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

        when (event.flowType) {
            PurchaseRequestAcceptBarrier.FLOW_TYPE -> {
                if (event.success) {
                    purchaseRequestAcceptBarrierAggregate.handleBarrierEvent(event.orderNo, event.barrierType)
                } else {
                    log.warn("Barrier failed: orderNo=${event.orderNo}, barrierType=${event.barrierType}, sending FAILED event")
                    stateMachineService.sendEvent(event.orderNo, OrderEvent.PURCHASE_REQUEST_ACCEPT_FAILED)
                }
            }
            CdoaAcceptBarrier.FLOW_TYPE -> {
                if (event.success) {
                    cdoaAcceptBarrierAggregate.handleBarrierEvent(event.orderNo, event.barrierType)
                } else {
                    log.warn("Barrier failed: orderNo=${event.orderNo}, barrierType=${event.barrierType}, sending FAILED event")
                    stateMachineService.sendEvent(event.orderNo, OrderEvent.CDOA_ACCEPT_FAILED)
                }
            }
            else -> log.warn("Unknown flowType: ${event.flowType}")
        }
    }
}

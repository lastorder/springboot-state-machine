package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.infrastructure.kafka.dto.ChangeTriggerEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ChangeTriggerProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendPurchaseRequestAccept(
        orderNo: String,
        market: com.example.statemachine.domain.enums.Market,
    ) {
        log.info("Sending PURCHASE_REQUEST_ACCEPT event: orderNo=$orderNo, market=$market")
        val event =
            ChangeTriggerEvent(
                orderNo = orderNo,
                market = market,
                eventType = "PURCHASE_REQUEST_ACCEPT",
            )
        kafkaTemplate.send(KafkaTopics.CHANGE_TRIGGER, orderNo, event)
    }

    fun sendCdoaAccept(
        orderNo: String,
        market: com.example.statemachine.domain.enums.Market,
    ) {
        log.info("Sending CDOA_ACCEPT event: orderNo=$orderNo, market=$market")
        val event =
            ChangeTriggerEvent(
                orderNo = orderNo,
                market = market,
                eventType = "CDOA_ACCEPT",
            )
        kafkaTemplate.send(KafkaTopics.CHANGE_TRIGGER, orderNo, event)
    }
}

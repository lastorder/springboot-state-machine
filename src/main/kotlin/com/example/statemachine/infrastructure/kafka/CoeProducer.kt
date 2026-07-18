package com.example.statemachine.infrastructure.kafka

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class CoeProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendCoeEventByOrderNo(orderNo: String) {
        log.info("Sending COE event: orderNo={}", orderNo)
        val event =
            mapOf(
                "orderNo" to orderNo,
                "eventType" to "COE_ORDER_CREATED",
            )
        kafkaTemplate.send(KafkaTopics.COE_ORDER_CREATED, orderNo, event)
    }
}

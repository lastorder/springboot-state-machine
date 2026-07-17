package com.example.statemachine.infrastructure.kafka

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class CoeProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val COE_ORDER_CREATED_TOPIC = "coe.order.created"
    }

    /**
     * 发送 COE 事件（使用 orderNo）
     */
    fun sendCoeEventByOrderNo(orderNo: String) {
        log.info("Sending COE event: orderNo={}", orderNo)
        val event =
            mapOf(
                "orderNo" to orderNo,
                "eventType" to "COE_ORDER_CREATED",
            )
        kafkaTemplate.send(COE_ORDER_CREATED_TOPIC, orderNo, event)
    }

    /**
     * 发送 COE 事件（使用 orderId）
     * @deprecated Use sendCoeEventByOrderNo instead
     */
    @Deprecated("Use sendCoeEventByOrderNo instead")
    fun sendCoeEvent(orderId: Long) {
        log.info("Sending COE event: orderId={}", orderId)
        val event =
            mapOf(
                "orderId" to orderId,
                "eventType" to "COE_ORDER_CREATED",
            )
        kafkaTemplate.send(COE_ORDER_CREATED_TOPIC, orderId.toString(), event)
    }
}

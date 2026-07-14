package com.example.statemachine.statemachine.action

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.kafka.CoeProducer
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component

@Component
class SendCoeAction(
    private val coeProducer: CoeProducer,
) : Action<OrderStatus, OrderEvent> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus, OrderEvent>) {
        val message = context.message
        val orderId = message.headers.get("orderId", Long::class.java)

        if (orderId == null) {
            log.error("Missing orderId header")
            return
        }

        log.info("Sending COE event for orderId={}", orderId)
        coeProducer.sendCoeEvent(orderId)
    }
}

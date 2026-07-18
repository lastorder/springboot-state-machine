package com.example.statemachine.statemachine.action

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.kafka.CoeProducer
import com.example.statemachine.order.barrier.OrderInitBarrierAggregate
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component

@Component
class SendCoeAction(
    private val coeProducer: CoeProducer,
    private val orderInitBarrierAggregate: OrderInitBarrierAggregate,
) : Action<OrderStatus, OrderEvent> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus, OrderEvent>) {
        val orderNo = extractOrderNo(context)

        if (orderNo.isNullOrBlank()) {
            log.error("Cannot determine orderNo from context")
            return
        }

        log.info("Sending COE event for orderNo={}", orderNo)
        coeProducer.sendCoeEventByOrderNo(orderNo)

        log.info("Initializing barrier aggregate for orderNo={}", orderNo)
        orderInitBarrierAggregate.initialize(orderNo)
    }

    private fun extractOrderNo(context: StateContext<OrderStatus, OrderEvent>): String? =
        context.message?.headers?.get("orderNo") as? String ?: context.stateMachine.id
}

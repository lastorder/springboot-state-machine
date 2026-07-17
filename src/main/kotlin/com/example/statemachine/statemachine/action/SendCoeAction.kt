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
        // 从 message header 获取 orderNo
        val orderNo =
            context.message?.headers?.get("orderNo") as? String
                // fallback: 从状态机 ID 获取（machineId 就是 orderNo）
                ?: context.stateMachine.id

        if (orderNo.isNullOrBlank()) {
            log.error("Cannot determine orderNo from context")
            return
        }

        log.info("Sending COE event for orderNo={}", orderNo)
        coeProducer.sendCoeEventByOrderNo(orderNo)
    }
}

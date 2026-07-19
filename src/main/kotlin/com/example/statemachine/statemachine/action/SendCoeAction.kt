package com.example.statemachine.statemachine.action

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.StateContext
import com.example.statemachine.application.barrier.OrderInitBarrierAggregate
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.kafka.CoeProducer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SendCoeAction(
    private val coeProducer: CoeProducer,
    private val orderInitBarrierAggregate: OrderInitBarrierAggregate,
) : Action<OrderStatus> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus>): ActionResult {
        val orderNo = OrderActionUtils.extractOrderNo(context)

        return try {
            log.info("Sending COE event for orderNo={}", orderNo)
            coeProducer.sendCoeEventByOrderNo(orderNo)

            log.info("Initializing barrier aggregate for orderNo={}", orderNo)
            orderInitBarrierAggregate.initialize(orderNo)

            ActionResult.success()
        } catch (e: Exception) {
            log.error("Failed to send COE event: orderNo={}", orderNo, e)
            ActionResult.technicalError("Failed to send COE event: ${e.message}", e)
        }
    }
}

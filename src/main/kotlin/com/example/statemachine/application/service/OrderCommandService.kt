package com.example.statemachine.application.service

import com.example.statemachine.application.task.OrderEventPayload
import com.example.statemachine.application.task.OrderStateMachineTaskSpec
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.task.scheduler.TaskScheduler
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OrderCommandService(
    private val taskScheduler: TaskScheduler,
    private val orderStateMachineTaskSpec: OrderStateMachineTaskSpec,
) {
    fun submitOrderEvent(
        orderNo: String,
        event: OrderEvent,
        headers: Map<String, Any?> = emptyMap(),
    ) {
        val instanceId = generateInstanceId(orderNo, event)
        val payload = OrderEventPayload(orderNo, event, headers)
        taskScheduler.submit(orderStateMachineTaskSpec, instanceId, payload)
    }

    private fun generateInstanceId(
        orderNo: String,
        event: OrderEvent,
    ): String = "order-$orderNo-${event.name}-${UUID.randomUUID()}"
}

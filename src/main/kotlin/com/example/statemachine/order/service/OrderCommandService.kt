package com.example.statemachine.order.service

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.order.task.OrderEventPayload
import com.example.statemachine.order.task.OrderStateMachineTaskSpec
import com.example.statemachine.task.scheduler.TaskScheduler
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class OrderCommandService(
    private val taskScheduler: TaskScheduler,
    private val orderStateMachineTaskSpec: OrderStateMachineTaskSpec,
) {
    fun submitOrderEvent(
        orderId: Long,
        event: OrderEvent,
        headers: Map<String, Any?> = emptyMap(),
    ) {
        val instanceId = generateInstanceId(orderId, event)
        val payload = OrderEventPayload(orderId, event, headers)
        taskScheduler.submit(orderStateMachineTaskSpec, instanceId, payload)
    }

    private fun generateInstanceId(
        orderId: Long,
        event: OrderEvent,
    ): String = "order-$orderId-${event.name}-${UUID.randomUUID()}"
}

package com.example.statemachine.order.service

import com.example.statemachine.commandinbox.domain.Command
import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.dto.CommandMetadata
import com.example.statemachine.commandinbox.dto.CommandSubmitResult
import com.example.statemachine.commandinbox.service.CommandBus
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.order.handler.OrderStateMachineSpec
import org.springframework.stereotype.Service

@Service
class OrderCommandService(
    private val commandBus: CommandBus,
    private val orderStateMachineSpec: OrderStateMachineSpec,
) {
    fun submitOrderEvent(
        orderId: Long,
        event: OrderEvent,
        headers: Map<String, Any?> = emptyMap(),
        metadata: CommandMetadata? = null,
        idempotencyKey: String? = null,
        priority: CommandPriority? = null,
    ): CommandSubmitResult =
        orderStateMachineSpec.submit(
            orderId = orderId,
            event = event,
            headers = headers,
            metadata = metadata,
            idempotencyKey = idempotencyKey,
            priority = priority,
        )

    fun getCommandStatus(
        orderId: Long,
        commandId: Long,
    ): Command? = commandBus.getCommandStatus(orderId.toString(), commandId)
}

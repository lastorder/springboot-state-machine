package com.example.statemachine.order.handler

import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.dto.CommandMetadata
import com.example.statemachine.commandinbox.handler.CommandContext
import com.example.statemachine.commandinbox.handler.CommandResult
import com.example.statemachine.commandinbox.handler.CommandSpec
import com.example.statemachine.commandinbox.service.CommandBus
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.statemachine.service.StateMachineService
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

data class OrderEventPayload(
    val event: OrderEvent,
    val headers: Map<String, Any?> = emptyMap(),
)

@Component
class OrderStateMachineSpec(
    private val commandBus: CommandBus,
    private val stateMachineService: StateMachineService,
) : CommandSpec<OrderEventPayload, Unit> {
    companion object {
        const val COMMAND_TYPE = "ORDER_STATE_TRANSITION"
    }

    override val commandType: String = COMMAND_TYPE
    override val payloadType: KClass<OrderEventPayload> = OrderEventPayload::class
    override val responseType: KClass<Unit> = Unit::class
    override val defaultPriority: CommandPriority = CommandPriority.URGENT

    override fun handle(context: CommandContext<OrderEventPayload>): CommandResult<Unit> {
        val payload = context.payload
        val orderId =
            context.groupId.toLongOrNull()
                ?: return CommandResult.Skipped("Invalid groupId: ${context.groupId}")

        val success =
            stateMachineService.sendEvent(
                orderId = orderId,
                event = payload.event,
                headers = payload.headers.filterValues { it != null }.mapValues { it!! },
            )

        return if (success) {
            CommandResult.Success()
        } else {
            CommandResult.Skipped("State machine rejected event: ${payload.event}")
        }
    }

    fun submit(
        orderId: Long,
        event: OrderEvent,
        headers: Map<String, Any?> = emptyMap(),
        metadata: CommandMetadata? = null,
        idempotencyKey: String? = null,
        priority: CommandPriority? = null,
    ) = commandBus.submit(
        spec = this,
        groupId = orderId.toString(),
        payload = OrderEventPayload(event, headers),
        metadata = metadata,
        idempotencyKey = idempotencyKey,
        priority = priority ?: defaultPriority,
    )
}

package com.example.statemachine.order.task

import com.example.statemachine.statemachine.service.StateMachineService
import com.example.statemachine.task.spec.LockingTaskSpec
import com.example.statemachine.task.spec.TaskContext
import com.example.statemachine.task.spec.TaskResult
import net.javacrumbs.shedlock.core.LockProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OrderStateMachineTaskSpec(
    private val stateMachineService: StateMachineService,
    lockProvider: LockProvider,
) : LockingTaskSpec<OrderEventPayload>(
        lockProvider = lockProvider,
        lockKeyProvider = { ctx -> "order:${ctx.payload.orderId}" },
    ) {
    override val taskName: String = TASK_NAME
    override val maxRetries: Int = 5
    override val payloadClass: Class<OrderEventPayload> = OrderEventPayload::class.java

    override fun executeWithLock(context: TaskContext<OrderEventPayload>): TaskResult {
        val payload = context.payload
        log.info("Processing order event: orderId={}, event={}", payload.orderId, payload.event)

        return try {
            val accepted =
                stateMachineService.sendEvent(
                    payload.orderId,
                    payload.event,
                    payload.headers.filterValues { it != null }.mapValues { it.value!! },
                )

            if (accepted) {
                TaskResult.success("Event ${payload.event} accepted")
            } else {
                TaskResult.fail("Event ${payload.event} rejected by state machine")
            }
        } catch (e: Exception) {
            log.error("State machine error for order {}", payload.orderId, e)
            TaskResult.retry("State machine error: ${e.message}")
        }
    }

    companion object {
        const val TASK_NAME = "order-state-machine"
        private val log = LoggerFactory.getLogger(OrderStateMachineTaskSpec::class.java)
    }
}

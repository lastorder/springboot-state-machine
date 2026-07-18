package com.example.statemachine.order.task

import com.example.statemachine.statemachine.service.StateMachineService
import com.example.statemachine.task.spec.LockingTaskSpec
import com.example.statemachine.task.spec.RetryStrategy
import com.example.statemachine.task.spec.TaskContext
import com.example.statemachine.task.spec.TaskResult
import net.javacrumbs.shedlock.core.LockProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class OrderStateMachineTaskSpec(
    private val stateMachineService: StateMachineService,
    lockProvider: LockProvider,
) : LockingTaskSpec<OrderEventPayload>(
        lockProvider = lockProvider,
        lockKeyProvider = { ctx -> "order:${ctx.payload.orderNo}" },
    ) {
    override val taskName: String = TASK_NAME
    override val maxRetries: Int = 5
    override val retryStrategy: RetryStrategy =
        RetryStrategy.exponentialBackoff(Duration.ofSeconds(3), 2.0)
    override val payloadClass: Class<OrderEventPayload> = OrderEventPayload::class.java

    override fun executeWithLock(context: TaskContext<OrderEventPayload>): TaskResult {
        val payload = context.payload
        log.info("Processing order event: orderNo={}, event={}", payload.orderNo, payload.event)

        return try {
            val accepted =
                stateMachineService.sendEvent(
                    payload.orderNo,
                    payload.event,
                    payload.headers.filterValues { it != null }.mapValues { it.value!! },
                )

            if (accepted) {
                TaskResult.success("Event ${payload.event} accepted")
            } else {
                TaskResult.failWithoutRetry("Event ${payload.event} rejected by state machine")
            }
        } catch (e: Exception) {
            log.error("State machine error for order {}", payload.orderNo, e)
            TaskResult.fail("State machine error: ${e.message}", e)
        }
    }

    companion object {
        const val TASK_NAME = "order-state-machine"
        private val log = LoggerFactory.getLogger(OrderStateMachineTaskSpec::class.java)
    }
}

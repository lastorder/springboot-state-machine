package com.example.statemachine.application.task

import com.example.statemachine.core.StateMachineFactory
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
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
    private val stateMachineFactory: StateMachineFactory<OrderStatus>,
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
                sendEvent(
                    payload.orderNo,
                    payload.event,
                    payload.headers.filterValues { it != null }.mapValues { it.value!! },
                )

            if (accepted) {
                log.info("Event {} accepted for order {}", payload.event, payload.orderNo)
            } else {
                log.warn("Event {} rejected for order {}, invalid transition", payload.event, payload.orderNo)
            }
            TaskResult.success("Event ${payload.event} processed, accepted=$accepted")
        } catch (e: Exception) {
            log.error("State machine error for order {}", payload.orderNo, e)
            TaskResult.fail("State machine error: ${e.message}", e)
        }
    }

    fun sendEvent(
        orderNo: String,
        event: OrderEvent,
        headers: Map<String, Any> = emptyMap(),
    ): Boolean {
        log.debug("Sending event: orderNo=$orderNo, event=$event")

        return try {
            val stateMachine = stateMachineFactory.create(orderNo)
            val result =
                stateMachine.sendEvent(
                    event = event,
                    headers = headers + ("orderNo" to orderNo),
                )
            result
        } catch (e: Exception) {
            log.error("Error sending event: orderNo=$orderNo, event=$event", e)
            false
        }
    }

    companion object {
        const val TASK_NAME = "order-state-machine"
        private val log = LoggerFactory.getLogger(OrderStateMachineTaskSpec::class.java)
    }
}

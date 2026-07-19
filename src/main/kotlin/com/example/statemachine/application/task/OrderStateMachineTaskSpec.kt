package com.example.statemachine.application.task

import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.core.StateMachine
import com.example.statemachine.core.TransitionTable
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
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
    private val orderJpaRepository: OrderJpaRepository,
    private val transitionTable: TransitionTable<OrderStatus>,
    private val stateMachineListener: StateChangedListener<OrderStatus>,
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

        val order = orderJpaRepository.findByOrderNo(payload.orderNo)
        val currentState = order?.status ?: OrderStatus.INIT

        val stateMachine =
            StateMachine.restore(
                id = payload.orderNo,
                currentState = currentState,
                initialState = OrderStatus.INIT,
                transitionTable = transitionTable,
                listener = stateMachineListener,
            )

        val headers = payload.headers.filterValues { it != null }.mapValues { it.value!! }
        val result = stateMachine.sendEvent(payload.event, headers + ("orderNo" to payload.orderNo))

        return when {
            result.accepted -> {
                log.info("Event {} accepted for order {}", payload.event, payload.orderNo)
                TaskResult.success("Event ${payload.event} processed")
            }
            result.shouldRetry() -> {
                log.error("Event {} failed with technical error for order {}", payload.event, payload.orderNo)
                TaskResult.fail("Technical error", retryable = true)
            }
            else -> {
                log.warn("Event {} rejected for order {}, reason={}", payload.event, payload.orderNo, result.failureReason)
                TaskResult.failWithoutRetry("Event rejected: ${result.failureReason}")
            }
        }
    }

    companion object {
        const val TASK_NAME = "order-state-machine"
        private val log = LoggerFactory.getLogger(OrderStateMachineTaskSpec::class.java)
    }
}

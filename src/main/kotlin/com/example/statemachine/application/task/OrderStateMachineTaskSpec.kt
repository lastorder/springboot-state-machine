package com.example.statemachine.application.task

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.statemachine.config.StateMachineListener
import com.example.statemachine.task.spec.LockingTaskSpec
import com.example.statemachine.task.spec.RetryStrategy
import com.example.statemachine.task.spec.TaskContext
import com.example.statemachine.task.spec.TaskResult
import net.javacrumbs.shedlock.core.LockProvider
import org.slf4j.LoggerFactory
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.data.jpa.JpaRepositoryStateMachine
import org.springframework.statemachine.data.jpa.JpaStateMachineRepository
import org.springframework.statemachine.support.DefaultStateMachineContext
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class OrderStateMachineTaskSpec(
    private val stateMachineFactory: StateMachineFactory<OrderStatus, OrderEvent>,
    private val jpaStateMachineRepository: JpaStateMachineRepository,
    private val stateMachineListener: StateMachineListener,
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
        val machineId = orderNo
        log.debug("Sending event: orderNo=$orderNo, event=$event")

        val stateMachine = stateMachineFactory.getStateMachine(machineId)

        return try {
            stateMachine.addStateListener(stateMachineListener)

            restoreStateMachine(stateMachine, machineId).block()
            stateMachine.startReactively().block()

            val message =
                MessageBuilder
                    .withPayload(event)
                    .setHeader("orderNo", orderNo)
                    .apply { headers.forEach { (k, v) -> setHeader(k, v) } }
                    .build()

            val result =
                stateMachine
                    .sendEvent(Mono.just(message))
                    .next()
                    .map { it.resultType.name == "ACCEPTED" }
                    .onErrorReturn(false)
                    .block() ?: false

            if (result) {
                persistStateMachine(stateMachine, machineId)
            }

            result
        } catch (e: Exception) {
            log.error("Error sending event: orderNo=$orderNo, event=$event", e)
            false
        } finally {
            stateMachine.stopReactively().block()
        }
    }

    private fun restoreStateMachine(
        stateMachine: StateMachine<OrderStatus, OrderEvent>,
        machineId: String,
    ): Mono<Void> {
        val entity = jpaStateMachineRepository.findById(machineId)
        return if (entity.isPresent) {
            val state = OrderStatus.valueOf(entity.get().state)
            resetStateMachineContext(stateMachine, state, machineId)
        } else {
            resetStateMachineContext(stateMachine, OrderStatus.INIT, machineId)
        }
    }

    private fun resetStateMachineContext(
        stateMachine: StateMachine<OrderStatus, OrderEvent>,
        state: OrderStatus,
        machineId: String,
    ): Mono<Void> {
        val context =
            DefaultStateMachineContext<OrderStatus, OrderEvent>(
                state,
                null,
                null,
                null,
                null,
                machineId,
            )
        return stateMachine.stateMachineAccessor.withRegion().resetStateMachineReactively(context)
    }

    private fun persistStateMachine(
        stateMachine: StateMachine<OrderStatus, OrderEvent>,
        machineId: String,
    ) {
        val entity = JpaRepositoryStateMachine()
        entity.machineId = machineId
        entity.state = stateMachine.state.id.name
        jpaStateMachineRepository.save(entity)
    }

    companion object {
        const val TASK_NAME = "order-state-machine"
        private val log = LoggerFactory.getLogger(OrderStateMachineTaskSpec::class.java)
    }
}

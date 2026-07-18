package com.example.statemachine.application.task

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
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
    private val stateMachineListener: org.springframework.statemachine.listener.StateMachineListenerAdapter<OrderStatus, OrderEvent>,
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
    ): Boolean = sendEvent(orderNo, event, emptyMap())

    fun sendEvent(
        orderNo: String,
        event: OrderEvent,
        headers: Map<String, Any>,
    ): Boolean {
        val machineId = orderNo
        log.info("Sending event: orderNo=$orderNo, event=$event, machineId=$machineId")

        val stateMachine = stateMachineFactory.getStateMachine(machineId)

        return try {
            stateMachine.addStateListener(stateMachineListener)
            restoreStateMachine(stateMachine, machineId).block()

            log.debug("Starting state machine before sending event")
            stateMachine.startReactively().block()

            log.debug("State machine state after start: ${stateMachine.state.id}")

            val message =
                MessageBuilder
                    .withPayload(event)
                    .setHeader("orderNo", orderNo)
                    .apply { headers.forEach { (k, v) -> setHeader(k, v) } }
                    .build()

            val result = sendEventReactive(stateMachine, message).block() ?: false
            log.info("Event send result: orderNo=$orderNo, event=$event, accepted=$result, newState=${stateMachine.state.id}")

            if (result) {
                persistStateMachine(stateMachine, machineId)
                log.debug("Persisted state machine: machineId=$machineId, state=${stateMachine.state.id}")
            }

            result
        } catch (e: Exception) {
            log.error("Error sending event: orderNo=$orderNo, event=$event", e)
            false
        } finally {
            stopStateMachine(stateMachine).block()
        }
    }

    fun getCurrentState(orderNo: String): OrderStatus? {
        val machineId = orderNo
        val stateMachine = stateMachineFactory.getStateMachine(machineId)

        return try {
            restoreStateMachine(stateMachine, machineId).block()
            stateMachine.state.id
        } catch (e: Exception) {
            log.error("Error getting current state: orderNo=$orderNo", e)
            null
        } finally {
            stopStateMachine(stateMachine).block()
        }
    }

    fun initializeStateMachine(
        orderNo: String,
        initialState: OrderStatus = OrderStatus.INIT,
    ) {
        val machineId = orderNo
        val stateMachine = stateMachineFactory.getStateMachine(machineId)

        try {
            resetStateMachineContext(stateMachine, initialState, machineId).block()
            persistStateMachine(stateMachine, machineId)
            log.info("Initialized state machine: orderNo=$orderNo, initialState=$initialState")
        } catch (e: Exception) {
            log.error("Error initializing state machine: orderNo=$orderNo", e)
        } finally {
            stopStateMachine(stateMachine).block()
        }
    }

    private fun sendEventReactive(
        stateMachine: StateMachine<OrderStatus, OrderEvent>,
        message: org.springframework.messaging.Message<OrderEvent>,
    ): Mono<Boolean> =
        stateMachine
            .sendEvent(Mono.just(message))
            .doOnNext { result ->
                log.debug("Event result: ${result.resultType}, event=${message.payload}, state=${stateMachine.state.id}")
            }.next()
            .map { it.resultType == org.springframework.statemachine.StateMachineEventResult.ResultType.ACCEPTED }
            .onErrorReturn(false)

    private fun stopStateMachine(stateMachine: StateMachine<OrderStatus, OrderEvent>): Mono<Void> = stateMachine.stopReactively()

    private fun restoreStateMachine(
        stateMachine: StateMachine<OrderStatus, OrderEvent>,
        machineId: String,
    ): Mono<Void> {
        val stateMachineEntity = jpaStateMachineRepository.findById(machineId)
        return if (stateMachineEntity.isPresent) {
            val entity = stateMachineEntity.get()
            val state = OrderStatus.valueOf(entity.state)
            resetStateMachineContext(stateMachine, state, machineId)
        } else {
            log.debug("No existing state machine found, initializing to INIT state: machineId=$machineId")
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
        return stateMachine.stateMachineAccessor
            .withRegion()
            .resetStateMachineReactively(context)
    }

    private fun persistStateMachine(
        stateMachine: StateMachine<OrderStatus, OrderEvent>,
        machineId: String,
    ) {
        val entity = JpaRepositoryStateMachine()
        entity.machineId = machineId
        entity.state = stateMachine.state.id.name
        jpaStateMachineRepository.save(entity)
        log.debug("Persisted state machine state: machineId=$machineId, state=${entity.state}")
    }

    companion object {
        const val TASK_NAME = "order-state-machine"
        private val log = LoggerFactory.getLogger(OrderStateMachineTaskSpec::class.java)
    }
}

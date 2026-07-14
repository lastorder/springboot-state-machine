package com.example.statemachine.statemachine.service

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.data.jpa.JpaStateMachineRepository
import org.springframework.statemachine.support.DefaultStateMachineContext
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class StateMachineService(
    private val stateMachineFactory: StateMachineFactory<OrderStatus, OrderEvent>,
    private val jpaStateMachineRepository: JpaStateMachineRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendEvent(
        orderId: Long,
        event: OrderEvent,
    ): Boolean = sendEvent(orderId, event, emptyMap())

    fun sendEvent(
        orderId: Long,
        event: OrderEvent,
        headers: Map<String, Any>,
    ): Boolean {
        val machineId = orderId.toString()
        log.info("Sending event: orderId=$orderId, event=$event, machineId=$machineId")

        val stateMachine = stateMachineFactory.getStateMachine(machineId)

        return try {
            restoreStateMachine(stateMachine, machineId).block()
            log.debug("Restored state machine state: ${stateMachine.state.id}")

            val message =
                MessageBuilder
                    .withPayload(event)
                    .setHeader("orderId", orderId)
                    .apply { headers.forEach { (k, v) -> setHeader(k, v) } }
                    .build()

            val result = sendEventReactive(stateMachine, message).block() ?: false
            log.info("Event send result: orderId=$orderId, event=$event, accepted=$result, newState=${stateMachine.state.id}")

            if (result) {
                persistStateMachine(stateMachine, machineId)
                log.debug("Persisted state machine: machineId=$machineId, state=${stateMachine.state.id}")
            }

            result
        } catch (e: Exception) {
            log.error("Error sending event: orderId=$orderId, event=$event", e)
            false
        } finally {
            stopStateMachine(stateMachine).block()
        }
    }

    fun getCurrentState(orderId: Long): OrderStatus? {
        val machineId = orderId.toString()
        val stateMachine = stateMachineFactory.getStateMachine(machineId)

        return try {
            restoreStateMachine(stateMachine, machineId).block()
            stateMachine.state.id
        } catch (e: Exception) {
            log.error("Error getting current state: orderId=$orderId", e)
            null
        } finally {
            stopStateMachine(stateMachine).block()
        }
    }

    fun initializeStateMachine(
        orderId: Long,
        initialState: OrderStatus = OrderStatus.INIT,
    ) {
        val machineId = orderId.toString()
        val stateMachine = stateMachineFactory.getStateMachine(machineId)

        try {
            resetStateMachineContext(stateMachine, initialState, machineId).block()
            persistStateMachine(stateMachine, machineId)
            log.info("Initialized state machine: orderId=$orderId, initialState=$initialState")
        } catch (e: Exception) {
            log.error("Error initializing state machine: orderId=$orderId", e)
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
            .next()
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
            Mono.empty()
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
        val entity =
            org.springframework.statemachine.data.jpa
                .JpaRepositoryStateMachine()
        entity.machineId = machineId
        entity.state = stateMachine.state.id.name
        jpaStateMachineRepository.save(entity)
    }
}

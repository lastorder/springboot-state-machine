package com.example.statemachine.service

import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.data.jpa.JpaStateMachineRepository
import org.springframework.statemachine.support.DefaultStateMachineContext
import org.springframework.stereotype.Service

@Service
class StateMachineService(
    private val stateMachineFactory: StateMachineFactory<OrderStatus, OrderEvent>,
    private val jpaStateMachineRepository: JpaStateMachineRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendEvent(
        orderId: Long,
        event: OrderEvent,
    ): Boolean {
        return sendEvent(orderId, event, emptyMap())
    }

    fun sendEvent(
        orderId: Long,
        event: OrderEvent,
        headers: Map<String, Any>,
    ): Boolean {
        val machineId = orderId.toString()
        log.info("Sending event: orderId=$orderId, event=$event, machineId=$machineId")

        val stateMachine = stateMachineFactory.getStateMachine(machineId)

        try {
            restoreStateMachine(stateMachine, machineId)
            log.debug("Restored state machine state: ${stateMachine.state.id}")

            val message =
                MessageBuilder
                    .withPayload(event)
                    .setHeader("orderId", orderId)
                    .apply { headers.forEach { (k, v) -> setHeader(k, v) } }
                    .build()

            val result = stateMachine.sendEvent(message)
            log.info("Event send result: orderId=$orderId, event=$event, accepted=$result, newState=${stateMachine.state.id}")

            if (result) {
                persistStateMachine(stateMachine, machineId)
                log.debug("Persisted state machine: machineId=$machineId, state=${stateMachine.state.id}")
            }

            return result
        } catch (e: Exception) {
            log.error("Error sending event: orderId=$orderId, event=$event", e)
            return false
        } finally {
            stateMachine.stop()
        }
    }

    fun getCurrentState(orderId: Long): OrderStatus? {
        val machineId = orderId.toString()
        val stateMachine = stateMachineFactory.getStateMachine(machineId)

        return try {
            restoreStateMachine(stateMachine, machineId)
            stateMachine.state.id
        } catch (e: Exception) {
            log.error("Error getting current state: orderId=$orderId", e)
            null
        } finally {
            stateMachine.stop()
        }
    }

    fun initializeStateMachine(
        orderId: Long,
        initialState: OrderStatus = OrderStatus.CREATED,
    ) {
        val machineId = orderId.toString()
        val stateMachine = stateMachineFactory.getStateMachine(machineId)

        try {
            stateMachine.stateMachineAccessor.doWithRegion { region ->
                region.resetStateMachine(
                    DefaultStateMachineContext(
                        initialState,
                        null,
                        null,
                        null,
                        null,
                        machineId,
                    ),
                )
            }
            persistStateMachine(stateMachine, machineId)
            log.info("Initialized state machine: orderId=$orderId, initialState=$initialState")
        } catch (e: Exception) {
            log.error("Error initializing state machine: orderId=$orderId", e)
        } finally {
            stateMachine.stop()
        }
    }

    private fun restoreStateMachine(
        stateMachine: org.springframework.statemachine.StateMachine<OrderStatus, OrderEvent>,
        machineId: String,
    ) {
        val stateMachineEntity = jpaStateMachineRepository.findById(machineId)
        if (stateMachineEntity.isPresent) {
            val entity = stateMachineEntity.get()
            val state = OrderStatus.valueOf(entity.state)
            stateMachine.stateMachineAccessor.doWithRegion { region ->
                region.resetStateMachine(
                    DefaultStateMachineContext(
                        state,
                        null,
                        null,
                        null,
                        null,
                        machineId,
                    ),
                )
            }
        }
    }

    private fun persistStateMachine(
        stateMachine: org.springframework.statemachine.StateMachine<OrderStatus, OrderEvent>,
        machineId: String,
    ) {
        val entity = org.springframework.statemachine.data.jpa.JpaRepositoryStateMachine()
        entity.machineId = machineId
        entity.state = stateMachine.state.id.name
        jpaStateMachineRepository.save(entity)
    }
}

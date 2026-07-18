package com.example.statemachine.statemachine.service

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
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
    private val stateMachineListener: org.springframework.statemachine.listener.StateMachineListenerAdapter<OrderStatus, OrderEvent>,
    private val orderJpaRepository: OrderJpaRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

    @Deprecated("Use sendEvent(orderNo, event) instead")
    fun sendEventByOrderNo(
        orderNo: String,
        event: OrderEvent,
    ): Boolean = sendEvent(orderNo, event)

    @Deprecated("Use sendEvent(orderNo, event, headers) instead")
    fun sendEventByOrderNo(
        orderNo: String,
        event: OrderEvent,
        headers: Map<String, Any>,
    ): Boolean = sendEvent(orderNo, event, headers)

    fun getCurrentStateByOrderNo(orderNo: String): OrderStatus? {
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

    fun initializeStateMachineByOrderNo(
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

    @Deprecated("Use sendEvent(orderNo, event) instead")
    fun sendEvent(
        orderId: Long,
        event: OrderEvent,
    ): Boolean = sendEvent(orderId, event, emptyMap())

    @Deprecated("Use sendEvent(orderNo, event, headers) instead")
    fun sendEvent(
        orderId: Long,
        event: OrderEvent,
        headers: Map<String, Any>,
    ): Boolean {
        val orderNo = orderJpaRepository.findIdByOrderNo(headers["orderNo"] as? String ?: "")
        if (orderNo == null && orderId > 0) {
            val entity = orderJpaRepository.findById(orderId).orElse(null)
            if (entity != null) {
                return sendEvent(entity.orderNo, event, headers)
            }
        }

        val orderNoFromHeaders = headers["orderNo"] as? String
        if (orderNoFromHeaders != null) {
            return sendEvent(orderNoFromHeaders, event, headers)
        }

        log.warn("Cannot determine orderNo for orderId=$orderId, event=$event")
        return false
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
        val entity =
            org.springframework.statemachine.data.jpa
                .JpaRepositoryStateMachine()
        entity.machineId = machineId
        entity.state = stateMachine.state.id.name
        jpaStateMachineRepository.save(entity)
        log.debug("Persisted state machine state: machineId=$machineId, state=${entity.state}")
    }
}

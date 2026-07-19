package com.example.statemachine.statemachine.config

import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.api.StateContext
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.kafka.OrderEventProducer
import com.example.statemachine.infrastructure.kafka.dto.OrderStatusChangeEvent
import com.example.statemachine.infrastructure.persistence.entity.StateMachineHistoryEntity
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import com.example.statemachine.infrastructure.persistence.repository.StateMachineHistoryJpaRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Component
class StateMachineListener(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderEventProducer: OrderEventProducer,
    private val stateMachineHistoryJpaRepository: StateMachineHistoryJpaRepository,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate,
) : StateChangedListener<OrderStatus> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onStateChanged(context: StateContext<OrderStatus>) {
        val orderNo = context.machineId
        val sourceState = context.sourceState
        val targetState = context.targetState
        val event = context.event
        val headers = context.headers

        log.info("State changed: orderNo={}, {} -> {}", orderNo, sourceState, targetState)

        transactionTemplate.executeWithoutResult {
            syncOrderStatus(orderNo, targetState, sourceState, event, headers)
        }

        try {
            orderEventProducer.sendStatusChangeEvent(
                OrderStatusChangeEvent(
                    orderId = orderNo.hashCode().toLong(),
                    fromStatus = sourceState,
                    toStatus = targetState,
                    event = event as? com.example.statemachine.domain.enums.OrderEvent,
                    timestamp = Instant.now(),
                ),
            )
        } catch (e: Exception) {
            log.error("Failed to send status change event: orderNo={}", orderNo, e)
        }
    }

    private fun syncOrderStatus(
        orderNo: String,
        newStatus: OrderStatus,
        fromStatus: OrderStatus,
        event: Enum<*>?,
        headers: Map<String, Any?>,
    ) {
        if (orderNo.isBlank()) {
            return
        }

        val updated = orderJpaRepository.updateStatusByOrderNo(orderNo, newStatus)
        if (updated > 0) {
            log.info("Synced order status: orderNo={}, status={}", orderNo, newStatus)
        } else {
            log.warn("Order not found for status sync: orderNo={}", orderNo)
        }

        saveHistory(orderNo, fromStatus, newStatus, event, headers)
    }

    private fun saveHistory(
        machineId: String,
        fromState: OrderStatus,
        toState: OrderStatus,
        event: Enum<*>?,
        headers: Map<String, Any?>,
    ) {
        try {
            val history =
                StateMachineHistoryEntity(
                    machineId = machineId,
                    fromState = fromState.name,
                    toState = toState.name,
                    event = event?.name,
                    headers = if (headers.isNotEmpty()) objectMapper.writeValueAsString(headers) else null,
                )
            stateMachineHistoryJpaRepository.save(history)
            log.debug("Saved state machine history: machineId={}, {} -> {}", machineId, fromState, toState)
        } catch (e: Exception) {
            log.error("Failed to save state machine history: machineId={}", machineId, e)
            throw e
        }
    }
}

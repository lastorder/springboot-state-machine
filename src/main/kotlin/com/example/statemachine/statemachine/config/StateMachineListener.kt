package com.example.statemachine.statemachine.config

import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.api.StateContext
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.kafka.OrderEventProducer
import com.example.statemachine.infrastructure.kafka.dto.OrderStatusChangeEvent
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Component
class StateMachineListener(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderEventProducer: OrderEventProducer,
    private val transactionTemplate: TransactionTemplate,
) : StateChangedListener<OrderStatus> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onStateChanged(context: StateContext<OrderStatus>) {
        val orderNo = context.machineId
        val sourceState = context.sourceState
        val targetState = context.targetState

        log.info("State changed: orderNo={}, {} -> {}", orderNo, sourceState, targetState)

        syncOrderStatus(orderNo, targetState, sourceState, context.event)
    }

    private fun syncOrderStatus(
        orderNo: String,
        newStatus: OrderStatus,
        fromStatus: OrderStatus,
        event: Enum<*>?,
    ) {
        if (orderNo.isBlank()) {
            return
        }

        transactionTemplate.executeWithoutResult {
            val updated = orderJpaRepository.updateStatusByOrderNo(orderNo, newStatus)
            if (updated > 0) {
                log.info("Synced order status: orderNo={}, status={}", orderNo, newStatus)
            } else {
                log.warn("Order not found for status sync: orderNo={}", orderNo)
            }

            try {
                orderEventProducer.sendStatusChangeEvent(
                    OrderStatusChangeEvent(
                        orderId = orderNo.hashCode().toLong(),
                        fromStatus = fromStatus,
                        toStatus = newStatus,
                        event = event as? com.example.statemachine.domain.enums.OrderEvent,
                        timestamp = Instant.now(),
                    ),
                )
            } catch (e: Exception) {
                log.error("Failed to send status change event: orderNo={}", orderNo, e)
            }
        }
    }
}

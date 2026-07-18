package com.example.statemachine.statemachine.action

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.rest.DealClient
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component

@Component
class SyncDealAction(
    private val dealClient: DealClient,
) : Action<OrderStatus, OrderEvent> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus, OrderEvent>) {
        val orderNo = OrderActionUtils.extractOrderNo(context)

        if (orderNo.isNullOrBlank()) {
            log.error("Cannot determine orderNo from context")
            return
        }

        log.info("Syncing order to deal service: orderNo={}", orderNo)
        try {
            dealClient.syncOrderByOrderNo(orderNo)
            log.info("Successfully synced order to deal service: orderNo={}", orderNo)
        } catch (e: Exception) {
            log.error("Failed to sync order to deal service: orderNo={}", orderNo, e)
        }
    }
}

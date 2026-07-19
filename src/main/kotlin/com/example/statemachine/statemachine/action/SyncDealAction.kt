package com.example.statemachine.statemachine.action

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.StateContext
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.rest.DealClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SyncDealAction(
    private val dealClient: DealClient,
) : Action<OrderStatus> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus>): ActionResult {
        val orderNo = OrderActionUtils.extractOrderNo(context)

        log.info("Syncing order to deal service: orderNo={}", orderNo)
        return try {
            dealClient.syncOrderByOrderNo(orderNo)
            log.info("Successfully synced order to deal service: orderNo={}", orderNo)
            ActionResult.success()
        } catch (e: Exception) {
            log.error("Failed to sync order to deal service: orderNo={}", orderNo, e)
            ActionResult.technicalError("Failed to sync order: ${e.message}", e)
        }
    }
}

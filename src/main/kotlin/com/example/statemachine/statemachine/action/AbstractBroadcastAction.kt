package com.example.statemachine.statemachine.action

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.StateContext
import com.example.statemachine.application.barrier.MarketAwareBarrierAggregate
import com.example.statemachine.domain.enums.Market
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import org.slf4j.LoggerFactory

abstract class AbstractBroadcastAction(
    private val orderJpaRepository: OrderJpaRepository,
    private val barrierAggregate: MarketAwareBarrierAggregate,
) : Action<OrderStatus> {
    protected val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus>): ActionResult {
        val orderNo = OrderActionUtils.extractOrderNo(context)

        val order =
            orderJpaRepository.findByOrderNo(orderNo)
                ?: run {
                    log.error("Order not found: orderNo=$orderNo")
                    return ActionResult.businessError("Order not found: $orderNo")
                }

        log.info("${actionName()}: orderNo=$orderNo, market=${order.market}")

        return try {
            barrierAggregate.initialize(orderNo, order.market)
            broadcast(orderNo, order.market)
            ActionResult.success()
        } catch (e: Exception) {
            log.error("Failed to ${actionName()}: orderNo=$orderNo", e)
            ActionResult.technicalError("${actionName()} failed: ${e.message}", e)
        }
    }

    protected abstract fun actionName(): String

    protected abstract fun broadcast(
        orderNo: String,
        market: Market,
    )
}

package com.example.statemachine.statemachine.action

import com.example.statemachine.application.barrier.MarketAwareBarrierAggregate
import com.example.statemachine.domain.enums.Market
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action

abstract class AbstractBroadcastAction(
    private val orderJpaRepository: OrderJpaRepository,
    private val barrierAggregate: MarketAwareBarrierAggregate,
) : Action<OrderStatus, OrderEvent> {
    protected val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus, OrderEvent>) {
        val orderNo = OrderActionUtils.extractOrderNo(context)

        if (orderNo.isNullOrBlank()) {
            log.error("Cannot determine orderNo from context")
            return
        }

        val order =
            orderJpaRepository.findByOrderNo(orderNo)
                ?: run {
                    log.error("Order not found: orderNo=$orderNo")
                    return
                }

        log.info("${actionName()}: orderNo=$orderNo, market=${order.market}")

        barrierAggregate.initialize(orderNo, order.market)
        broadcast(orderNo, order.market)
    }

    protected abstract fun actionName(): String

    protected abstract fun broadcast(
        orderNo: String,
        market: Market,
    )
}

package com.example.statemachine.statemachine.action

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.kafka.ChangeTriggerProducer
import com.example.statemachine.infrastructure.persistence.entity.OrderJpaEntity
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import com.example.statemachine.order.barrier.CdoaAcceptBarrierAggregate
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component

@Component
class BroadcastCdoaAcceptAction(
    private val changeTriggerProducer: ChangeTriggerProducer,
    private val orderJpaRepository: OrderJpaRepository,
    private val cdoaAcceptBarrierAggregate: CdoaAcceptBarrierAggregate,
) : Action<OrderStatus, OrderEvent> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus, OrderEvent>) {
        val orderNo = OrderActionUtils.extractOrderNo(context)

        if (orderNo.isNullOrBlank()) {
            log.error("Cannot determine orderNo from context")
            return
        }

        val order: OrderJpaEntity =
            orderJpaRepository.findByOrderNo(orderNo)
                ?: run {
                    log.error("Order not found: orderNo=$orderNo")
                    return
                }

        log.info("Initializing barrier for CDOA_ACCEPT: orderNo=$orderNo, market=${order.market}")
        cdoaAcceptBarrierAggregate.initialize(orderNo, order.market)

        log.info("Broadcasting CDOA_ACCEPT event to external systems: orderNo=$orderNo, market=${order.market}")
        changeTriggerProducer.sendCdoaAccept(orderNo, order.market)
    }
}

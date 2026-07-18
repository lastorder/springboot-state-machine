package com.example.statemachine.order.barrier

import com.example.statemachine.barrieraggregate.BarrierAggregateRepository
import com.example.statemachine.barrieraggregate.MarketAwareBarrierAggregate
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.statemachine.service.StateMachineService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class PurchaseRequestAcceptBarrierAggregate(
    repository: BarrierAggregateRepository,
    @Lazy private val stateMachineService: StateMachineService,
) : MarketAwareBarrierAggregate(repository, PurchaseRequestAcceptBarrier) {
    override fun onAllBarriersPassed(aggregateKey: String) {
        stateMachineService.sendEvent(aggregateKey, OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS)
    }
}

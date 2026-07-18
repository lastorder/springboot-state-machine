package com.example.statemachine.order.barrier

import com.example.statemachine.barrieraggregate.BarrierAggregateRepository
import com.example.statemachine.barrieraggregate.MarketAwareBarrierAggregate
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.statemachine.service.StateMachineService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class CdoaAcceptBarrierAggregate(
    repository: BarrierAggregateRepository,
    @Lazy private val stateMachineService: StateMachineService,
) : MarketAwareBarrierAggregate(repository, CdoaAcceptBarrier) {
    override fun onAllBarriersPassed(aggregateKey: String) {
        stateMachineService.sendEvent(aggregateKey, OrderEvent.CDOA_ACCEPT_SUCCESS)
    }
}

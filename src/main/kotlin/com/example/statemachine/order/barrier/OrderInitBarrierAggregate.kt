package com.example.statemachine.order.barrier

import com.example.statemachine.barrieraggregate.BarrierAggregate
import com.example.statemachine.barrieraggregate.BarrierAggregateRepository
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.statemachine.service.StateMachineService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class OrderInitBarrierAggregate(
    repository: BarrierAggregateRepository,
    @Lazy private val stateMachineService: StateMachineService,
) : BarrierAggregate(repository) {
    override val requiredBarriers: Set<String> =
        setOf(
            OrderInitBarrier.VOM,
            OrderInitBarrier.DOM,
        )

    override fun onAllBarriersPassed(aggregateKey: String) {
        stateMachineService.sendEvent(aggregateKey, OrderEvent.VOM)
    }
}

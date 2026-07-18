package com.example.statemachine.application.barrier

import com.example.statemachine.application.service.OrderCommandService
import com.example.statemachine.barrieraggregate.BarrierAggregate
import com.example.statemachine.barrieraggregate.BarrierAggregateRepository
import com.example.statemachine.domain.enums.OrderEvent
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class OrderInitBarrierAggregate(
    repository: BarrierAggregateRepository,
    @Lazy private val orderCommandService: OrderCommandService,
) : BarrierAggregate(repository) {
    override val requiredBarriers: Set<String> =
        setOf(
            OrderInitBarrier.VOM,
            OrderInitBarrier.DOM,
        )

    override fun onAllBarriersPassed(aggregateKey: String) {
        orderCommandService.submitOrderEvent(aggregateKey, OrderEvent.VOM)
    }
}

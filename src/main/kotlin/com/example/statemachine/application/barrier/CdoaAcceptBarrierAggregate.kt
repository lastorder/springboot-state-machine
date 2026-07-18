package com.example.statemachine.application.barrier

import com.example.statemachine.application.service.OrderCommandService
import com.example.statemachine.barrieraggregate.BarrierAggregateRepository
import com.example.statemachine.domain.enums.OrderEvent
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class CdoaAcceptBarrierAggregate(
    repository: BarrierAggregateRepository,
    @Lazy private val orderCommandService: OrderCommandService,
) : MarketAwareBarrierAggregate(repository, CdoaAcceptBarrier) {
    override fun onAllBarriersPassed(aggregateKey: String) {
        orderCommandService.submitOrderEvent(aggregateKey, OrderEvent.CDOA_ACCEPT_SUCCESS)
    }
}

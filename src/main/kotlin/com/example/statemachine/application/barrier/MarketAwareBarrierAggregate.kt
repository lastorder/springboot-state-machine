package com.example.statemachine.application.barrier

import com.example.statemachine.barrieraggregate.BarrierAggregate
import com.example.statemachine.barrieraggregate.BarrierAggregateRepository
import com.example.statemachine.domain.enums.Market

abstract class MarketAwareBarrierAggregate(
    repository: BarrierAggregateRepository,
    private val barrierProvider: MarketBarrierProvider,
) : BarrierAggregate(repository) {
    final override val requiredBarriers: Set<String> = emptySet()

    fun initialize(
        aggregateKey: String,
        market: Market,
    ) {
        val barriers = barrierProvider.getBarriersForMarket(market)
        initializeWithBarriers(aggregateKey, barriers)
    }
}

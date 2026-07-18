package com.example.statemachine.barrieraggregate

import com.example.statemachine.domain.enums.Market
import com.example.statemachine.order.barrier.MarketBarrierProvider

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

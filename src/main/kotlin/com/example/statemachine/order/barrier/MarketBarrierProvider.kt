package com.example.statemachine.order.barrier

import com.example.statemachine.domain.enums.Market

interface MarketBarrierProvider {
    val deMarketBarriers: Set<String>
    val itMarketBarriers: Set<String>

    fun getBarriersForMarket(market: Market): Set<String> =
        when (market) {
            Market.DE -> deMarketBarriers
            Market.IT -> itMarketBarriers
        }
}

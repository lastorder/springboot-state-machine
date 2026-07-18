package com.example.statemachine.order.barrier

object PurchaseRequestAcceptBarrier : MarketBarrierProvider {
    const val FLOW_TYPE = "PR_ACCEPT"

    override val deMarketBarriers = CommonBarrier.DE_MARKET_BARRIERS
    override val itMarketBarriers = CommonBarrier.IT_MARKET_BARRIERS
}

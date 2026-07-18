package com.example.statemachine.application.barrier

object CdoaAcceptBarrier : MarketBarrierProvider {
    const val FLOW_TYPE = "CDOA"

    override val deMarketBarriers = CommonBarrier.DE_MARKET_BARRIERS
    override val itMarketBarriers = CommonBarrier.IT_MARKET_BARRIERS
}

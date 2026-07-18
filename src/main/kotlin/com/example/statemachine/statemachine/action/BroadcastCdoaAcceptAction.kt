package com.example.statemachine.statemachine.action

import com.example.statemachine.application.barrier.CdoaAcceptBarrierAggregate
import com.example.statemachine.domain.enums.Market
import com.example.statemachine.infrastructure.kafka.ChangeTriggerProducer
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import org.springframework.stereotype.Component

@Component
class BroadcastCdoaAcceptAction(
    changeTriggerProducer: ChangeTriggerProducer,
    orderJpaRepository: OrderJpaRepository,
    cdoaAcceptBarrierAggregate: CdoaAcceptBarrierAggregate,
) : AbstractBroadcastAction(orderJpaRepository, cdoaAcceptBarrierAggregate) {
    private val producer = changeTriggerProducer

    override fun actionName(): String = "BroadcastCdoaAccept"

    override fun broadcast(
        orderNo: String,
        market: Market,
    ) {
        producer.sendCdoaAccept(orderNo, market)
    }
}

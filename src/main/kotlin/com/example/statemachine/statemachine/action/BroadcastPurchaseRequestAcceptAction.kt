package com.example.statemachine.statemachine.action

import com.example.statemachine.application.barrier.PurchaseRequestAcceptBarrierAggregate
import com.example.statemachine.domain.enums.Market
import com.example.statemachine.infrastructure.kafka.ChangeTriggerProducer
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import org.springframework.stereotype.Component

@Component
class BroadcastPurchaseRequestAcceptAction(
    changeTriggerProducer: ChangeTriggerProducer,
    orderJpaRepository: OrderJpaRepository,
    purchaseRequestAcceptBarrierAggregate: PurchaseRequestAcceptBarrierAggregate,
) : AbstractBroadcastAction(orderJpaRepository, purchaseRequestAcceptBarrierAggregate) {
    private val producer = changeTriggerProducer

    override fun actionName(): String = "BroadcastPurchaseRequestAccept"

    override fun broadcast(
        orderNo: String,
        market: Market,
    ) {
        producer.sendPurchaseRequestAccept(orderNo, market)
    }
}

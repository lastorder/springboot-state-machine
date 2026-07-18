package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.domain.enums.Market
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.kafka.dto.DomEvent
import com.example.statemachine.infrastructure.kafka.dto.PrApprovedEvent
import com.example.statemachine.infrastructure.kafka.dto.VomEvent
import com.example.statemachine.order.barrier.OrderInitBarrier
import com.example.statemachine.order.barrier.OrderInitBarrierAggregate
import com.example.statemachine.statemachine.service.StateMachineService
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OrderEventConsumerTest {
    private lateinit var stateMachineService: StateMachineService
    private lateinit var orderInitBarrierAggregate: OrderInitBarrierAggregate
    private lateinit var orderEventConsumer: OrderEventConsumer

    @BeforeEach
    fun setUp() {
        stateMachineService = mockk(relaxed = true)
        orderInitBarrierAggregate = mockk(relaxed = true)
        orderEventConsumer = OrderEventConsumer(stateMachineService, orderInitBarrierAggregate)
    }

    @Test
    @DisplayName("Should handle PR_APPROVED event with orderNo")
    fun testOnPrApproved() {
        val event =
            PrApprovedEvent(
                orderId = 1L,
                orderNo = "ORD-001",
                productId = "PROD-123",
                productName = "Test Product",
                quantity = 5,
                amount = 100.0,
                market = Market.DE,
            )
        val record = ConsumerRecord("pr.approved", 0, 0L, "1", event)

        orderEventConsumer.onPrApproved(record)

        verify {
            stateMachineService.sendEvent(
                orderNo = "ORD-001",
                event = OrderEvent.PR_APPROVED,
                headers =
                    match<Map<String, Any>> {
                        it["orderNo"] == "ORD-001" && it["market"] == "DE"
                    },
            )
        }
    }

    @Test
    @DisplayName("Should handle VOM event")
    fun testOnVom() {
        val event = VomEvent(orderNo = "ORD-001")
        val record = ConsumerRecord("factory.vom", 0, 0L, "1", event)

        orderEventConsumer.onVom(record)

        verify { orderInitBarrierAggregate.handleBarrierEvent("ORD-001", OrderInitBarrier.VOM) }
    }

    @Test
    @DisplayName("Should handle DOM event")
    fun testOnDom() {
        val event = DomEvent(orderNo = "ORD-001")
        val record = ConsumerRecord("factory.dom", 0, 0L, "1", event)

        orderEventConsumer.onDom(record)

        verify { orderInitBarrierAggregate.handleBarrierEvent("ORD-001", OrderInitBarrier.DOM) }
    }

    @Test
    @DisplayName("Should handle VOM_FAILED event with orderNo")
    fun testOnVomFailedWithOrderNo() {
        val event = VomEvent(orderNo = "ORD-001")
        val record = ConsumerRecord("factory.vom.failed", 0, 0L, "1", event)

        orderEventConsumer.onVomFailed(record)

        verify {
            stateMachineService.sendEvent(
                orderNo = "ORD-001",
                event = OrderEvent.VOM_FAILED,
            )
        }
    }
}

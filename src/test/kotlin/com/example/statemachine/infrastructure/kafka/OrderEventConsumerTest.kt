package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.kafka.dto.DomEvent
import com.example.statemachine.infrastructure.kafka.dto.PrApprovedEvent
import com.example.statemachine.infrastructure.kafka.dto.VomEvent
import com.example.statemachine.statemachine.service.StateMachineService
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderEventConsumerTest {
    private lateinit var stateMachineService: StateMachineService
    private lateinit var orderEventConsumer: OrderEventConsumer

    @BeforeEach
    fun setUp() {
        stateMachineService = mockk(relaxed = true)
        orderEventConsumer = OrderEventConsumer(stateMachineService)
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
                amount = BigDecimal("100.00"),
            )
        val record = ConsumerRecord("pr.approved", 0, 0L, "1", event)

        orderEventConsumer.onPrApproved(record)

        verify {
            stateMachineService.sendEventByOrderNo(
                orderNo = "ORD-001",
                event = OrderEvent.PR_APPROVED,
                headers =
                    match<Map<String, Any>> {
                        it["orderNo"] == "ORD-001"
                    },
            )
        }
    }

    @Test
    @DisplayName("Should handle VOM event with orderNo")
    fun testOnVomWithOrderNo() {
        val event = VomEvent(orderNo = "ORD-001", orderId = 0L)
        val record = ConsumerRecord("factory.vom", 0, 0L, "1", event)

        orderEventConsumer.onVom(record)

        verify {
            stateMachineService.sendEventByOrderNo(
                orderNo = "ORD-001",
                event = OrderEvent.VOM,
            )
        }
    }

    @Test
    @DisplayName("Should handle DOM event with orderNo")
    fun testOnDomWithOrderNo() {
        val event = DomEvent(orderNo = "ORD-001", orderId = 0L)
        val record = ConsumerRecord("factory.dom", 0, 0L, "1", event)

        orderEventConsumer.onDom(record)

        verify {
            stateMachineService.sendEventByOrderNo(
                orderNo = "ORD-001",
                event = OrderEvent.DOM,
            )
        }
    }

    @Test
    @DisplayName("Should handle VOM_FAILED event with orderNo")
    fun testOnVomFailedWithOrderNo() {
        val event = VomEvent(orderNo = "ORD-001", orderId = 0L)
        val record = ConsumerRecord("factory.vom.failed", 0, 0L, "1", event)

        orderEventConsumer.onVomFailed(record)

        verify {
            stateMachineService.sendEventByOrderNo(
                orderNo = "ORD-001",
                event = OrderEvent.VOM_FAILED,
            )
        }
    }
}

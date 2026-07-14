package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.kafka.dto.DomEvent
import com.example.statemachine.infrastructure.kafka.dto.PrApprovedEvent
import com.example.statemachine.infrastructure.kafka.dto.VomEvent
import com.example.statemachine.order.service.OrderCommandService
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderEventConsumerTest {
    private lateinit var orderCommandService: OrderCommandService
    private lateinit var orderEventConsumer: OrderEventConsumer

    @BeforeEach
    fun setUp() {
        orderCommandService = mockk(relaxed = true)
        orderEventConsumer = OrderEventConsumer(orderCommandService)
    }

    @Test
    @DisplayName("Should handle PR_APPROVED event")
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
            orderCommandService.submitOrderEvent(
                orderId = 1L,
                event = OrderEvent.PR_APPROVED,
                headers =
                    match<Map<String, Any?>> {
                        it["orderNo"] == "ORD-001" &&
                            it["productId"] == "PROD-123" &&
                            it["productName"] == "Test Product" &&
                            it["quantity"] == 5 &&
                            it["amount"] == BigDecimal("100.00")
                    },
            )
        }
    }

    @Test
    @DisplayName("Should handle VOM event")
    fun testOnVom() {
        val event = VomEvent(orderId = 1L)
        val record = ConsumerRecord("factory.vom", 0, 0L, "1", event)

        orderEventConsumer.onVom(record)

        verify {
            orderCommandService.submitOrderEvent(
                orderId = 1L,
                event = OrderEvent.VOM,
            )
        }
    }

    @Test
    @DisplayName("Should handle DOM event")
    fun testOnDom() {
        val event = DomEvent(orderId = 1L)
        val record = ConsumerRecord("factory.dom", 0, 0L, "1", event)

        orderEventConsumer.onDom(record)

        verify {
            orderCommandService.submitOrderEvent(
                orderId = 1L,
                event = OrderEvent.DOM,
            )
        }
    }

    @Test
    @DisplayName("Should handle VOM_FAILED event")
    fun testOnVomFailed() {
        val event = VomEvent(orderId = 1L)
        val record = ConsumerRecord("factory.vom.failed", 0, 0L, "1", event)

        orderEventConsumer.onVomFailed(record)

        verify {
            orderCommandService.submitOrderEvent(
                orderId = 1L,
                event = OrderEvent.VOM_FAILED,
            )
        }
    }
}

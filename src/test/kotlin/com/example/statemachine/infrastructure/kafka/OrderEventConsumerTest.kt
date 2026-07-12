package com.example.statemachine.infrastructure.kafka

import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.domain.CommandSource
import com.example.statemachine.commandinbox.service.CommandInboxService
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.kafka.dto.InventoryCheckResponse
import com.example.statemachine.infrastructure.kafka.dto.InventoryOrderModified
import com.example.statemachine.infrastructure.kafka.dto.OrderDeliveredEvent
import com.example.statemachine.infrastructure.kafka.dto.OrderRefundedEvent
import com.example.statemachine.infrastructure.kafka.dto.OrderShippedEvent
import com.example.statemachine.infrastructure.kafka.dto.PaymentConfirmedEvent
import com.example.statemachine.infrastructure.kafka.dto.PricingResponse
import com.example.statemachine.order.service.OrderService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class OrderEventConsumerTest {
    private lateinit var commandInboxService: CommandInboxService
    private lateinit var orderService: OrderService
    private lateinit var orderEventConsumer: OrderEventConsumer

    @BeforeEach
    fun setUp() {
        commandInboxService = mockk(relaxed = true)
        orderService = mockk(relaxed = true)
        orderEventConsumer = OrderEventConsumer(commandInboxService, orderService)
    }

    @Test
    @DisplayName("Should handle inventory check response when available")
    fun testOnInventoryCheckResponse_Available() {
        val response = InventoryCheckResponse(
            orderId = 1L,
            available = true,
            inventoryReference = "INV-123",
        )
        val record = ConsumerRecord("inventory.check.response", 0, 0L, "1", response)

        orderEventConsumer.onInventoryCheckResponse(record)

        verify { orderService.markInventorySuccess(1L, "INV-123") }
        verify {
            commandInboxService.submitCommand(
                orderId = 1L,
                event = OrderEvent.INVENTORY_SUCCESS,
                source = CommandSource.KAFKA,
                sourceReference = any(),
                priority = CommandPriority.HIGH,
            )
        }
    }

    @Test
    @DisplayName("Should handle inventory check response when not available")
    fun testOnInventoryCheckResponse_NotAvailable() {
        val response = InventoryCheckResponse(
            orderId = 1L,
            available = false,
            inventoryReference = null,
        )
        val record = ConsumerRecord("inventory.check.response", 0, 0L, "1", response)

        orderEventConsumer.onInventoryCheckResponse(record)

        verify { orderService.markInventoryFailed(1L) }
        verify {
            commandInboxService.submitCommand(
                orderId = 1L,
                event = OrderEvent.INVENTORY_FAILED,
                source = CommandSource.KAFKA,
                sourceReference = any(),
                priority = CommandPriority.HIGH,
            )
        }
    }

    @Test
    @DisplayName("Should handle pricing response")
    fun testOnPricingResponse() {
        val response = PricingResponse(
            orderId = 1L,
            pricingReference = "PRICE-123",
            unitPrice = BigDecimal("50.00"),
            totalPrice = BigDecimal("250.00"),
        )
        val record = ConsumerRecord("pricing.response", 0, 0L, "1", response)

        orderEventConsumer.onPricingResponse(record)

        verify { orderService.markPricingSuccess(1L, "PRICE-123", BigDecimal("50.00")) }
        verify {
            commandInboxService.submitCommand(
                orderId = 1L,
                event = OrderEvent.PRICING_SUCCESS,
                source = CommandSource.KAFKA,
                sourceReference = any(),
                priority = CommandPriority.HIGH,
            )
        }
    }

    @Test
    @DisplayName("Should handle inventory order modified event")
    fun testOnInventoryOrderModified() {
        val event = InventoryOrderModified(
            orderId = 1L,
            modifiedProduct = "New Product",
            modifiedQuantity = 5,
            reason = "Stock update",
        )
        val record = ConsumerRecord("inventory.order.modified", 0, 0L, "1", event)

        orderEventConsumer.onInventoryOrderModified(record)

        verify {
            orderService.updateFromInventoryModification(
                orderId = 1L,
                modifiedProduct = "New Product",
                modifiedQuantity = 5,
                reason = "Stock update",
            )
        }
        verify {
            commandInboxService.submitCommand(
                orderId = 1L,
                event = OrderEvent.INVENTORY_MODIFIED,
                source = CommandSource.KAFKA,
                sourceReference = any(),
            )
        }
    }

    @Test
    @DisplayName("Should handle payment confirmed event")
    fun testOnPaymentConfirmed() {
        val event = PaymentConfirmedEvent(
            orderId = 1L,
            transactionId = "TXN-123",
            amount = BigDecimal("100.00"),
            confirmedAt = Instant.now(),
        )
        val record = ConsumerRecord("payment.confirmed", 0, 0L, "1", event)

        orderEventConsumer.onPaymentConfirmed(record)

        verify {
            commandInboxService.submitCommand(
                orderId = 1L,
                event = OrderEvent.CONFIRM_PAYMENT,
                source = CommandSource.KAFKA,
                sourceReference = any(),
                priority = CommandPriority.URGENT,
            )
        }
    }

    @Test
    @DisplayName("Should handle order shipped event")
    fun testOnOrderShipped() {
        val event = OrderShippedEvent(
            orderId = 1L,
            trackingNumber = "TRACK-123",
            shippedAt = Instant.now(),
        )
        val record = ConsumerRecord("order.shipped", 0, 0L, "1", event)

        orderEventConsumer.onOrderShipped(record)

        verify {
            commandInboxService.submitCommand(
                orderId = 1L,
                event = OrderEvent.SHIP,
                source = CommandSource.KAFKA,
                sourceReference = any(),
            )
        }
    }

    @Test
    @DisplayName("Should handle order delivered event")
    fun testOnOrderDelivered() {
        val event = OrderDeliveredEvent(
            orderId = 1L,
            deliveredAt = Instant.now(),
        )
        val record = ConsumerRecord("order.delivered", 0, 0L, "1", event)

        orderEventConsumer.onOrderDelivered(record)

        verify {
            commandInboxService.submitCommand(
                orderId = 1L,
                event = OrderEvent.DELIVER,
                source = CommandSource.KAFKA,
                sourceReference = any(),
            )
        }
    }

    @Test
    @DisplayName("Should handle order refunded event")
    fun testOnOrderRefunded() {
        val event = OrderRefundedEvent(
            orderId = 1L,
            refundAmount = BigDecimal("100.00"),
            reason = "Customer request",
            refundedAt = Instant.now(),
        )
        val record = ConsumerRecord("order.refunded", 0, 0L, "1", event)

        orderEventConsumer.onOrderRefunded(record)

        verify {
            commandInboxService.submitCommand(
                orderId = 1L,
                event = OrderEvent.REFUND,
                source = CommandSource.KAFKA,
                sourceReference = any(),
                priority = CommandPriority.URGENT,
            )
        }
    }
}

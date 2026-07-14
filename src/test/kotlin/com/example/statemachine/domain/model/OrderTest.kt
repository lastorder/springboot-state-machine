package com.example.statemachine.domain.model

import com.example.statemachine.domain.enums.OrderStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderTest {
    @Test
    @DisplayName("Should update order status")
    fun testUpdateStatus() {
        val order =
            Order(
                id = 1L,
                orderNo = "ORD-001",
                quantity = 5,
                status = OrderStatus.INIT,
            )

        order.updateStatus(OrderStatus.LOCAL_INITIALIZED)

        assertEquals(OrderStatus.LOCAL_INITIALIZED, order.status)
        assertNotNull(order.updatedAt)
    }

    @Test
    @DisplayName("Should create order from PR approved event")
    fun testFromPrApproved() {
        val order =
            Order.fromPrApproved(
                orderNo = "ORD-001",
                productId = "PROD-123",
                productName = "Test Product",
                quantity = 5,
                amount = BigDecimal("100.00"),
            )

        assertEquals("ORD-001", order.orderNo)
        assertEquals("PROD-123", order.productId)
        assertEquals("Test Product", order.productName)
        assertEquals(5, order.quantity)
        assertEquals(BigDecimal("100.00"), order.amount)
        assertEquals(OrderStatus.INIT, order.status)
    }

    @Test
    @DisplayName("Should create order with default quantity")
    fun testFromPrApproved_DefaultQuantity() {
        val order =
            Order.fromPrApproved(
                orderNo = "ORD-002",
                productId = null,
                productName = null,
                quantity = null,
                amount = null,
            )

        assertEquals("ORD-002", order.orderNo)
        assertEquals(1, order.quantity)
        assertEquals(OrderStatus.INIT, order.status)
    }

    @Test
    @DisplayName("Should have correct default state")
    fun testDefaultState() {
        val order = Order(orderNo = "ORD-003")

        assertEquals(OrderStatus.INIT, order.status)
        assertNotNull(order.createdAt)
        assertNotNull(order.updatedAt)
    }
}

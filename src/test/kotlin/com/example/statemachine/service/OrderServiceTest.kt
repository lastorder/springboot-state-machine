package com.example.statemachine.service

import com.example.statemachine.application.service.OrderService
import com.example.statemachine.domain.enums.Market
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.model.Order
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.presentation.dto.CreateOrderRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderServiceTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        orderService = OrderService(orderRepository)
    }

    @Test
    @DisplayName("Should create order")
    fun testCreateOrder() {
        val request = CreateOrderRequest("ORD-001", "PROD-123", "Test Product", 2, BigDecimal("100.00"), Market.DE)
        every { orderRepository.save(any()) } returns
            Order(
                id = 1L,
                orderNo = request.orderNo,
                productId = request.productId,
                productName = request.productName,
                quantity = request.quantity ?: 1,
                amount = request.amount,
                status = OrderStatus.INIT,
                market = request.market,
            )

        val result = orderService.createOrder(request)

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("ORD-001", result.orderNo)
        assertEquals("PROD-123", result.productId)
        assertEquals("Test Product", result.productName)
        assertEquals(2, result.quantity)
        assertEquals(BigDecimal("100.00"), result.amount)
        assertEquals(OrderStatus.INIT, result.status)
        assertEquals(Market.DE, result.market)
    }

    @Test
    @DisplayName("Should get order by id")
    fun testGetOrder() {
        val order =
            Order(
                id = 1L,
                orderNo = "ORD-001",
                productId = "PROD-123",
                productName = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.INIT,
                market = Market.DE,
            )
        every { orderRepository.findById(1L) } returns order

        val result = orderService.getOrder(1L)

        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals("ORD-001", result.orderNo)
        assertEquals(Market.DE, result.market)
    }

    @Test
    @DisplayName("Should return null for non-existent order")
    fun testGetOrderNotFound() {
        every { orderRepository.findById(999L) } returns null

        val result = orderService.getOrder(999L)

        assertNull(result)
    }

    @Test
    @DisplayName("Should get all orders")
    fun testGetAllOrders() {
        val orders =
            listOf(
                Order(
                    id = 1L,
                    orderNo = "ORD-001",
                    productId = "PROD-1",
                    productName = "Product 1",
                    quantity = 1,
                    amount = BigDecimal("10.00"),
                    status = OrderStatus.INIT,
                    market = Market.DE,
                ),
                Order(
                    id = 2L,
                    orderNo = "ORD-002",
                    productId = "PROD-2",
                    productName = "Product 2",
                    quantity = 2,
                    amount = BigDecimal("20.00"),
                    status = OrderStatus.LOCAL_INITIALIZED,
                    market = Market.IT,
                ),
            )
        every { orderRepository.findAll() } returns orders

        val result = orderService.getAllOrders()

        assertEquals(2, result.size)
    }

    @Test
    @DisplayName("Should update order status")
    fun testUpdateOrderStatus() {
        val order =
            Order(
                id = 1L,
                orderNo = "ORD-001",
                status = OrderStatus.INIT,
                market = Market.DE,
            )
        every { orderRepository.findById(1L) } returns order
        every { orderRepository.save(any()) } returns order

        val result = orderService.updateOrderStatus(1L, OrderStatus.LOCAL_INITIALIZED)

        assertEquals(true, result)
        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should return false when updating non-existent order")
    fun testUpdateOrderStatusNotFound() {
        every { orderRepository.findById(999L) } returns null

        val result = orderService.updateOrderStatus(999L, OrderStatus.LOCAL_INITIALIZED)

        assertEquals(false, result)
    }

    @Test
    @DisplayName("Should get order entity")
    fun testGetOrderEntity() {
        val order =
            Order(
                id = 1L,
                orderNo = "ORD-001",
                status = OrderStatus.INIT,
                market = Market.DE,
            )
        every { orderRepository.findById(1L) } returns order

        val result = orderService.getOrderEntity(1L)

        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals(Market.DE, result.market)
    }

    @Test
    @DisplayName("Should save order")
    fun testSaveOrder() {
        val order =
            Order(
                id = 1L,
                orderNo = "ORD-001",
                status = OrderStatus.INIT,
                market = Market.DE,
            )
        every { orderRepository.save(order) } returns order

        val result = orderService.saveOrder(order)

        assertNotNull(result)
        verify { orderRepository.save(order) }
    }
}

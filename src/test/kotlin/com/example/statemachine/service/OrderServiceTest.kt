package com.example.statemachine.service

import com.example.statemachine.controller.dto.CreateOrderRequest
import com.example.statemachine.domain.Order
import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.repository.OrderRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Optional

class OrderServiceTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var stateMachineService: StateMachineService
    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        stateMachineService = mockk()
        orderService = OrderService(orderRepository, stateMachineService)
    }

    @Test
    @DisplayName("Should create order successfully")
    fun testCreateOrder() {
        val request = CreateOrderRequest("Test Product", BigDecimal("100.00"))
        every { orderRepository.save(any()) } returns Order(
            id = 1L,
            product = request.product,
            amount = request.amount,
            status = OrderStatus.CREATED,
        )

        val result = orderService.createOrder(request)

        assertNotNull(result)
        assertEquals("Test Product", result.product)
        assertEquals(BigDecimal("100.00"), result.amount)
        assertEquals(OrderStatus.CREATED, result.status)
        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should get order by id")
    fun testGetOrder() {
        val order = Order(id = 1L, product = "Test Product", amount = BigDecimal("100.00"))
        every { orderRepository.findById(1L) } returns Optional.of(order)

        val result = orderService.getOrder(1L)

        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals("Test Product", result.product)
    }

    @Test
    @DisplayName("Should return null when order not found")
    fun testGetOrderNotFound() {
        every { orderRepository.findById(999L) } returns Optional.empty()

        val result = orderService.getOrder(999L)

        assertNull(result)
    }

    @Test
    @DisplayName("Should submit order successfully")
    fun testSubmitOrder() {
        val order = Order(id = 1L, product = "Test Product", amount = BigDecimal("100.00"))
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { stateMachineService.sendEvent(1L, OrderEvent.SUBMIT) } returns true
        every { orderRepository.save(any()) } returns order

        val result = orderService.submitOrder(1L)

        assertTrue(result)
        verify { stateMachineService.sendEvent(1L, OrderEvent.SUBMIT) }
    }

    @Test
    @DisplayName("Should fail to submit order in wrong status")
    fun testSubmitOrderWrongStatus() {
        val order = Order(
            id = 1L,
            product = "Test Product",
            amount = BigDecimal("100.00"),
            status = OrderStatus.PENDING_PAYMENT,
        )
        every { orderRepository.findById(1L) } returns Optional.of(order)

        val result = orderService.submitOrder(1L)

        assertEquals(false, result)
    }

    @Test
    @DisplayName("Should pay order successfully")
    fun testPayOrder() {
        val order = Order(
            id = 1L,
            product = "Test Product",
            amount = BigDecimal("100.00"),
            status = OrderStatus.PENDING_PAYMENT,
        )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every {
            stateMachineService.sendEvent(
                1L,
                OrderEvent.PAY,
                any(),
            )
        } returns true
        every { orderRepository.save(any()) } returns order

        val result = orderService.payOrder(1L, BigDecimal("100.00"))

        assertTrue(result)
    }

    @Test
    @DisplayName("Should cancel order successfully")
    fun testCancelOrder() {
        val order = Order(
            id = 1L,
            product = "Test Product",
            amount = BigDecimal("100.00"),
            status = OrderStatus.CREATED,
        )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { stateMachineService.sendEvent(1L, OrderEvent.CANCEL) } returns true
        every { orderRepository.save(any()) } returns order

        val result = orderService.cancelOrder(1L)

        assertTrue(result)
    }

    @Test
    @DisplayName("Should fail to cancel shipped order")
    fun testCancelShippedOrder() {
        val order = Order(
            id = 1L,
            product = "Test Product",
            amount = BigDecimal("100.00"),
            status = OrderStatus.SHIPPED,
        )
        every { orderRepository.findById(1L) } returns Optional.of(order)

        val result = orderService.cancelOrder(1L)

        assertEquals(false, result)
    }

    @Test
    @DisplayName("Should update order status")
    fun testUpdateOrderStatus() {
        val order = Order(
            id = 1L,
            product = "Test Product",
            amount = BigDecimal("100.00"),
            status = OrderStatus.PENDING_SHIPMENT,
        )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { orderRepository.save(any()) } returns order

        val result = orderService.updateOrderStatus(1L, OrderStatus.SHIPPED)

        assertTrue(result)
    }

    @Test
    @DisplayName("Should get all orders")
    fun testGetAllOrders() {
        val orders = listOf(
            Order(id = 1L, product = "Product 1", amount = BigDecimal("100.00")),
            Order(id = 2L, product = "Product 2", amount = BigDecimal("200.00")),
        )
        every { orderRepository.findAll() } returns orders

        val result = orderService.getAllOrders()

        assertEquals(2, result.size)
        assertEquals("Product 1", result[0].product)
        assertEquals("Product 2", result[1].product)
    }
}

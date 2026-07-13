package com.example.statemachine.service

import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.commandinbox.dto.CommandSubmitResult
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.model.Order
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.order.service.OrderCommandService
import com.example.statemachine.order.service.OrderService
import com.example.statemachine.presentation.dto.CreateOrderRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderServiceTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var orderCommandService: OrderCommandService
    private lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        orderCommandService = mockk(relaxed = true)
        orderService = OrderService(orderRepository, orderCommandService, maxRetries = 3)
    }

    @Test
    @DisplayName("Should create order and trigger validation")
    fun testCreateOrder() {
        val request = CreateOrderRequest("Test Product", 2, BigDecimal("100.00"))
        every { orderRepository.save(any()) } returns
            Order(
                id = 1L,
                product = request.product,
                quantity = request.quantity,
                amount = request.amount,
                status = OrderStatus.CREATED,
            )
        every { orderCommandService.submitOrderEvent(any(), any(), any()) } returns
            CommandSubmitResult(1L, "1", "ORDER_STATE_TRANSITION", CommandStatus.PENDING, "OK")

        val result = orderService.createOrder(request)

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("Test Product", result.product)
        assertEquals(2, result.quantity)
        assertEquals(BigDecimal("100.00"), result.amount)
        assertEquals(OrderStatus.CREATED, result.status)
    }

    @Test
    @DisplayName("Should get order by id")
    fun testGetOrder() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.CREATED,
            )
        every { orderRepository.findById(1L) } returns order

        val result = orderService.getOrder(1L)

        assertNotNull(result)
        assertEquals(1L, result!!.id)
    }

    @Test
    @DisplayName("Should return null for non-existent order")
    fun testGetOrderNotFound() {
        every { orderRepository.findById(999L) } returns null

        val result = orderService.getOrder(999L)

        assertEquals(null, result)
    }

    @Test
    @DisplayName("Should get all orders")
    fun testGetAllOrders() {
        val orders =
            listOf(
                Order(id = 1L, product = "Product 1", quantity = 1, amount = BigDecimal("10.00"), status = OrderStatus.CREATED),
                Order(id = 2L, product = "Product 2", quantity = 2, amount = BigDecimal("20.00"), status = OrderStatus.CREATED),
            )
        every { orderRepository.findAll() } returns orders

        val result = orderService.getAllOrders()

        assertEquals(2, result.size)
    }

    @Test
    @DisplayName("Should mark inventory success")
    fun testMarkInventorySuccess() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )
        every { orderRepository.findById(1L) } returns order
        every { orderRepository.save(any()) } returns order

        orderService.markInventorySuccess(1L, "INV-REF-123")

        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should mark inventory failed")
    fun testMarkInventoryFailed() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )
        every { orderRepository.findById(1L) } returns order
        every { orderRepository.save(any()) } returns order

        orderService.markInventoryFailed(1L)

        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should mark pricing success")
    fun testMarkPricingSuccess() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )
        every { orderRepository.findById(1L) } returns order
        every { orderRepository.save(any()) } returns order

        orderService.markPricingSuccess(1L, "PRICE-REF-123", BigDecimal("50.00"))

        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should update order status")
    fun testUpdateOrderStatus() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_PAYMENT,
            )
        every { orderRepository.findById(1L) } returns order
        every { orderRepository.save(any()) } returns order

        val result = orderService.updateOrderStatus(1L, OrderStatus.PAID)

        assertEquals(true, result)
        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should return false when updating non-existent order")
    fun testUpdateOrderStatus_NotFound() {
        every { orderRepository.findById(999L) } returns null

        val result = orderService.updateOrderStatus(999L, OrderStatus.PAID)

        assertEquals(false, result)
    }

    @Test
    @DisplayName("Should update order for payment")
    fun testUpdateOrderForPayment() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_PAYMENT,
            )
        every { orderRepository.findById(1L) } returns order
        every { orderRepository.save(any()) } returns order

        val result = orderService.updateOrderForPayment(1L, BigDecimal("100.00"))

        assertTrue(result)
        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should return false when paying order in wrong status")
    fun testUpdateOrderForPayment_WrongStatus() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.CREATED,
            )
        every { orderRepository.findById(1L) } returns order

        val result = orderService.updateOrderForPayment(1L, BigDecimal("100.00"))

        assertFalse(result)
    }

    @Test
    @DisplayName("Should cancel order")
    fun testUpdateOrderForCancellation() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_CONFIRMATION,
            )
        every { orderRepository.findById(1L) } returns order
        every { orderRepository.save(any()) } returns order

        val result = orderService.updateOrderForCancellation(1L)

        assertTrue(result)
        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should not cancel order in delivered status")
    fun testUpdateOrderForCancellation_DeliveredStatus() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.DELIVERED,
            )
        every { orderRepository.findById(1L) } returns order

        val result = orderService.updateOrderForCancellation(1L)

        assertFalse(result)
    }

    @Test
    @DisplayName("Should update from inventory modification")
    fun testUpdateFromInventoryModification() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_PAYMENT,
            )
        every { orderRepository.findById(1L) } returns order
        every { orderRepository.save(any()) } returns order

        orderService.updateFromInventoryModification(
            orderId = 1L,
            modifiedProduct = "New Product",
            modifiedQuantity = 5,
            reason = "Stock update",
        )

        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should not update when order not found for inventory modification")
    fun testUpdateFromInventoryModification_NotFound() {
        every { orderRepository.findById(999L) } returns null

        orderService.updateFromInventoryModification(
            orderId = 999L,
            modifiedProduct = "New Product",
            modifiedQuantity = 5,
            reason = "Stock update",
        )

        verify(exactly = 0) { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should get order entity")
    fun testGetOrderEntity() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.CREATED,
            )
        every { orderRepository.findById(1L) } returns order

        val result = orderService.getOrderEntity(1L)

        assertNotNull(result)
        assertEquals(1L, result!!.id)
    }
}

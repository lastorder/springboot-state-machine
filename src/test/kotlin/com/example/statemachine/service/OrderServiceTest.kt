package com.example.statemachine.service

import com.example.statemachine.controller.dto.CreateOrderRequest
import com.example.statemachine.controller.dto.ModifyOrderRequest
import com.example.statemachine.domain.Order
import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.repository.OrderRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        orderService = OrderService(orderRepository, stateMachineService, maxRetries = 3)
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
        every { stateMachineService.sendEvent(1L, OrderEvent.SUBMIT_VALIDATION) } returns true

        val result = orderService.createOrder(request)

        assertNotNull(result)
        assertEquals("Test Product", result.product)
        assertEquals(2, result.quantity)
        assertEquals(BigDecimal("100.00"), result.amount)
        assertEquals(OrderStatus.CREATED, result.status)
        verify { stateMachineService.sendEvent(1L, OrderEvent.SUBMIT_VALIDATION) }
    }

    @Test
    @DisplayName("Should get order by id")
    fun testGetOrder() {
        val order = Order(id = 1L, product = "Test Product", quantity = 2, amount = BigDecimal("100.00"))
        every { orderRepository.findById(1L) } returns Optional.of(order)

        val result = orderService.getOrder(1L)

        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals("Test Product", result.product)
        assertEquals(2, result.quantity)
    }

    @Test
    @DisplayName("Should return null when order not found")
    fun testGetOrderNotFound() {
        every { orderRepository.findById(999L) } returns Optional.empty()

        val result = orderService.getOrder(999L)

        assertNull(result)
    }

    @Test
    @DisplayName("Should retry validation from PENDING_VALIDATION status")
    fun testRetryValidation() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { orderRepository.save(any()) } returns order
        every { stateMachineService.sendEvent(1L, OrderEvent.RETRY_VALIDATION) } returns true

        val result = orderService.retryValidation(1L)

        assertTrue(result)
        verify { stateMachineService.sendEvent(1L, OrderEvent.RETRY_VALIDATION) }
    }

    @Test
    @DisplayName("Should fail retry validation when max retries exceeded")
    fun testRetryValidationMaxRetriesExceeded() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )
        order.incrementRetryCount()
        order.incrementRetryCount()
        order.incrementRetryCount()
        every { orderRepository.findById(1L) } returns Optional.of(order)

        val result = orderService.retryValidation(1L)

        assertFalse(result)
    }

    @Test
    @DisplayName("Should fail retry validation in wrong status")
    fun testRetryValidationWrongStatus() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_CONFIRMATION,
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)

        val result = orderService.retryValidation(1L)

        assertFalse(result)
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
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { orderRepository.save(any()) } returns order

        orderService.markInventorySuccess(1L, "INV-123")

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
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
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
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { orderRepository.save(any()) } returns order

        orderService.markPricingSuccess(1L, "PRICE-456", BigDecimal("50.00"))

        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should modify order in PENDING_CONFIRMATION status")
    fun testModifyOrderFromPendingConfirmation() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_CONFIRMATION,
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { orderRepository.save(any()) } returns order
        every { stateMachineService.sendEvent(any(), any<OrderEvent>(), any()) } returns true

        val request = ModifyOrderRequest(product = "New Product", quantity = 5)
        val result = orderService.modifyOrder(1L, request)

        assertTrue(result)
        verify { stateMachineService.sendEvent(1L, OrderEvent.MODIFY_ORDER, any()) }
    }

    @Test
    @DisplayName("Should modify order in PENDING_PAYMENT status")
    fun testModifyOrderFromPendingPayment() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_PAYMENT,
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { orderRepository.save(any()) } returns order
        every { stateMachineService.sendEvent(any(), any<OrderEvent>(), any()) } returns true

        val request = ModifyOrderRequest(quantity = 10)
        val result = orderService.modifyOrder(1L, request)

        assertTrue(result)
    }

    @Test
    @DisplayName("Should fail to modify order in wrong status")
    fun testModifyOrderWrongStatus() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PAID,
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)

        val request = ModifyOrderRequest(quantity = 10)
        val result = orderService.modifyOrder(1L, request)

        assertEquals(false, result)
    }

    @Test
    @DisplayName("Should confirm order")
    fun testConfirmOrder() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_CONFIRMATION,
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { orderRepository.save(any()) } returns order
        every { stateMachineService.sendEvent(1L, OrderEvent.USER_CONFIRM) } returns true

        val result = orderService.confirmOrder(1L)

        assertTrue(result)
        verify { stateMachineService.sendEvent(1L, OrderEvent.USER_CONFIRM) }
    }

    @Test
    @DisplayName("Should reject order")
    fun testRejectOrder() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_CONFIRMATION,
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { stateMachineService.sendEvent(1L, OrderEvent.USER_REJECT) } returns true

        val result = orderService.rejectOrder(1L, "Price too high")

        assertTrue(result)
        verify { stateMachineService.sendEvent(1L, OrderEvent.USER_REJECT) }
    }

    @Test
    @DisplayName("Should update inventory info")
    fun testUpdateInventoryInfo() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { orderRepository.save(any()) } returns order

        orderService.updateFromInventoryModification(1L, "Modified Product", 3, "Stock adjustment")

        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should update order from inventory modification")
    fun testUpdateFromInventoryModification() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { orderRepository.save(any()) } returns order

        orderService.updateFromInventoryModification(
            orderId = 1L,
            modifiedProduct = "Modified Product",
            modifiedQuantity = 3,
            reason = "Stock adjustment",
        )

        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("Should pay order")
    fun testPayOrder() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_PAYMENT,
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { stateMachineService.sendEvent(1L, OrderEvent.PAY, any()) } returns true
        every { orderRepository.save(any()) } returns order

        val result = orderService.payOrder(1L, BigDecimal("100.00"))

        assertTrue(result)
    }

    @Test
    @DisplayName("Should cancel order from CREATED status")
    fun testCancelOrder() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
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
    @DisplayName("Should cancel order from PENDING_VALIDATION status")
    fun testCancelOrderFromPendingValidation() {
        val order =
            Order(
                id = 1L,
                product = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                status = OrderStatus.PENDING_VALIDATION,
            )
        every { orderRepository.findById(1L) } returns Optional.of(order)
        every { stateMachineService.sendEvent(1L, OrderEvent.CANCEL) } returns true
        every { orderRepository.save(any()) } returns order

        val result = orderService.cancelOrder(1L)

        assertTrue(result)
    }

    @Test
    @DisplayName("Should get all orders")
    fun testGetAllOrders() {
        val orders =
            listOf(
                Order(id = 1L, product = "Product 1", quantity = 1, amount = BigDecimal("100.00")),
                Order(id = 2L, product = "Product 2", quantity = 2, amount = BigDecimal("200.00")),
            )
        every { orderRepository.findAll() } returns orders

        val result = orderService.getAllOrders()

        assertEquals(2, result.size)
        assertEquals("Product 1", result[0].product)
        assertEquals("Product 2", result[1].product)
    }
}

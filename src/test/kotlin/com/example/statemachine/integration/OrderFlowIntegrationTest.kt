package com.example.statemachine.integration

import com.example.statemachine.config.TestConfig
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.presentation.dto.CreateOrderRequest
import com.example.statemachine.presentation.dto.ModifyOrderRequest
import com.example.statemachine.presentation.dto.PaymentRequest
import com.example.statemachine.statemachine.service.StateMachineService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestConfig::class)
@ActiveProfiles("test")
class OrderFlowIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var stateMachineService: StateMachineService

    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        every { stateMachineService.sendEvent(any(), any<OrderEvent>()) } returns true
        every { stateMachineService.sendEvent(any(), any<OrderEvent>(), any()) } returns true
    }

    @Test
    @DisplayName("Should create order and trigger SUBMIT_VALIDATION event")
    fun testCreateOrder() {
        val request = CreateOrderRequest("Test Product", 2, BigDecimal("100.00"))

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.product").value("Test Product"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.quantity").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.amount").value(100.00))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CREATED"))
    }

    @Test
    @DisplayName("Should get order by id")
    fun testGetOrder() {
        val request = CreateOrderRequest("Test Product", 2, BigDecimal("100.00"))

        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        val orderId = response.get("id").asLong()

        mockMvc
            .perform(MockMvcRequestBuilders.get("/api/orders/$orderId"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(orderId))
            .andExpect(MockMvcResultMatchers.jsonPath("$.product").value("Test Product"))
    }

    @Test
    @DisplayName("Should retry validation for order in PENDING_VALIDATION status")
    fun testRetryValidation() {
        val createRequest = CreateOrderRequest("Test Product", 2, BigDecimal("100.00"))

        val createResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)),
                ).andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val orderId = createResponse.get("id").asLong()

        val order = orderRepository.findById(orderId)
        order?.status = OrderStatus.PENDING_VALIDATION
        orderRepository.save(order!!)

        mockMvc
            .perform(MockMvcRequestBuilders.post("/api/orders/$orderId/retry-validation"))
            .andExpect(MockMvcResultMatchers.status().isAccepted)
    }

    @Test
    @DisplayName("Should modify order")
    fun testModifyOrder() {
        val createRequest = CreateOrderRequest("Test Product", 2, BigDecimal("100.00"))

        val createResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)),
                ).andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val orderId = createResponse.get("id").asLong()

        val order = orderRepository.findById(orderId)
        order?.status = OrderStatus.PENDING_CONFIRMATION
        orderRepository.save(order!!)

        val modifyRequest = ModifyOrderRequest(product = "Modified Product", quantity = 5)
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .patch("/api/orders/$orderId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(modifyRequest)),
            ).andExpect(MockMvcResultMatchers.status().isAccepted)
    }

    @Test
    @DisplayName("Should confirm order")
    fun testConfirmOrder() {
        val createRequest = CreateOrderRequest("Test Product", 2, BigDecimal("100.00"))

        val createResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)),
                ).andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val orderId = createResponse.get("id").asLong()

        val order = orderRepository.findById(orderId)
        order?.status = OrderStatus.PENDING_CONFIRMATION
        orderRepository.save(order!!)

        mockMvc
            .perform(MockMvcRequestBuilders.post("/api/orders/$orderId/confirm"))
            .andExpect(MockMvcResultMatchers.status().isAccepted)
    }

    @Test
    @DisplayName("Should reject order")
    fun testRejectOrder() {
        val createRequest = CreateOrderRequest("Test Product", 2, BigDecimal("100.00"))

        val createResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)),
                ).andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val orderId = createResponse.get("id").asLong()

        val order = orderRepository.findById(orderId)
        order?.status = OrderStatus.PENDING_CONFIRMATION
        orderRepository.save(order!!)

        mockMvc
            .perform(MockMvcRequestBuilders.post("/api/orders/$orderId/reject?reason=Price%20too%20high"))
            .andExpect(MockMvcResultMatchers.status().isAccepted)
    }

    @Test
    @DisplayName("Should pay order")
    fun testPayOrder() {
        val createRequest = CreateOrderRequest("Test Product", 2, BigDecimal("100.00"))

        val createResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)),
                ).andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val orderId = createResponse.get("id").asLong()

        val order = orderRepository.findById(orderId)
        order?.status = OrderStatus.PENDING_PAYMENT
        orderRepository.save(order!!)

        val paymentRequest = PaymentRequest(BigDecimal("100.00"))
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/orders/$orderId/pay")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(paymentRequest)),
            ).andExpect(MockMvcResultMatchers.status().isAccepted)
    }

    @Test
    @DisplayName("Should cancel order")
    fun testCancelOrder() {
        val createRequest = CreateOrderRequest("Test Product", 2, BigDecimal("100.00"))

        val createResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)),
                ).andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val orderId = createResponse.get("id").asLong()

        mockMvc
            .perform(MockMvcRequestBuilders.post("/api/orders/$orderId/cancel"))
            .andExpect(MockMvcResultMatchers.status().isAccepted)
    }

    @Test
    @DisplayName("Should list all orders")
    fun testGetAllOrders() {
        val beforeCount =
            mockMvc
                .perform(MockMvcRequestBuilders.get("/api/orders"))
                .andReturn()
                .response.contentAsString
                .let {
                    objectMapper.readTree(it).size()
                }

        for (i in 1..3) {
            val request = CreateOrderRequest("Product $i", i, BigDecimal("$i.00"))
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(MockMvcResultMatchers.status().isCreated)
        }

        mockMvc
            .perform(MockMvcRequestBuilders.get("/api/orders"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(beforeCount + 3))
    }

    @Test
    @DisplayName("Should return 404 for non-existent order")
    fun testGetNonExistentOrder() {
        mockMvc
            .perform(MockMvcRequestBuilders.get("/api/orders/999999"))
            .andExpect(MockMvcResultMatchers.status().isNotFound)
    }
}

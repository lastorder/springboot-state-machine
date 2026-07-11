package com.example.statemachine.integration

import com.example.statemachine.config.TestConfig
import com.example.statemachine.controller.dto.CreateOrderRequest
import com.example.statemachine.controller.dto.PaymentRequest
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.repository.OrderRepository
import com.example.statemachine.service.StateMachineService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
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
        orderRepository.deleteAll()
        Mockito.reset(stateMachineService)
        Mockito.`when`(stateMachineService.sendEvent(Mockito.anyLong(), Mockito.any<OrderEvent>())).thenReturn(true)
        Mockito.`when`(stateMachineService.sendEvent(Mockito.anyLong(), Mockito.any<OrderEvent>(), Mockito.anyMap())).thenReturn(true)
    }

    @Test
    @DisplayName("Should create order via REST API")
    fun testCreateOrder() {
        val request = CreateOrderRequest("Test Product", BigDecimal("100.00"))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.product").value("Test Product"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.amount").value(100.00))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CREATED"))
    }

    @Test
    @DisplayName("Should get order by id")
    fun testGetOrder() {
        val request = CreateOrderRequest("Test Product", BigDecimal("100.00"))

        val result =
            mockMvc.perform(
                MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        val orderId = response.get("id").asLong()

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/$orderId"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(orderId))
            .andExpect(MockMvcResultMatchers.jsonPath("$.product").value("Test Product"))
    }

    @Test
    @DisplayName("Should complete full order flow")
    fun testFullOrderFlow() {
        val createRequest = CreateOrderRequest("Test Product", BigDecimal("100.00"))

        val createResult =
            mockMvc.perform(
                MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)),
            )
                .andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val orderId = createResponse.get("id").asLong()
        Assertions.assertNotNull(orderId)

        mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/$orderId/submit"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))

        val order = orderRepository.findById(orderId).orElse(null)
        Assertions.assertEquals(OrderStatus.PENDING_PAYMENT, order?.status)

        val paymentRequest = PaymentRequest(BigDecimal("100.00"))
        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/orders/$orderId/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))

        val paidOrder = orderRepository.findById(orderId).orElse(null)
        Assertions.assertEquals(OrderStatus.PAID, paidOrder?.status)
    }

    @Test
    @DisplayName("Should cancel order from CREATED status")
    fun testCancelOrder() {
        val createRequest = CreateOrderRequest("Test Product", BigDecimal("100.00"))

        val createResult =
            mockMvc.perform(
                MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)),
            )
                .andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val orderId = createResponse.get("id").asLong()

        mockMvc.perform(MockMvcRequestBuilders.post("/api/orders/$orderId/cancel"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))

        val cancelledOrder = orderRepository.findById(orderId).orElse(null)
        Assertions.assertEquals(OrderStatus.CANCELLED, cancelledOrder?.status)
    }

    @Test
    @DisplayName("Should list all orders")
    fun testGetAllOrders() {
        for (i in 1..3) {
            val request = CreateOrderRequest("Product $i", BigDecimal("$i.00"))
            mockMvc.perform(
                MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andExpect(MockMvcResultMatchers.status().isCreated)
        }

        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(3))
    }

    @Test
    @DisplayName("Should return 404 for non-existent order")
    fun testGetNonExistentOrder() {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/orders/999999"))
            .andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    @Test
    @DisplayName("Should fail to pay order with wrong status")
    fun testPayOrderWithWrongStatus() {
        val createRequest = CreateOrderRequest("Test Product", BigDecimal("100.00"))

        val createResult =
            mockMvc.perform(
                MockMvcRequestBuilders.post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)),
            )
                .andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val orderId = createResponse.get("id").asLong()

        val paymentRequest = PaymentRequest(BigDecimal("100.00"))
        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/orders/$orderId/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(paymentRequest)),
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
            .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(false))
    }
}

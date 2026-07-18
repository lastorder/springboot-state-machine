package com.example.statemachine.integration

import com.example.statemachine.domain.enums.Market
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.presentation.dto.CreateOrderRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(IntegrationTestConfig::class)
@ActiveProfiles("integration")
class OrderFlowIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var orderRepository: OrderRepository

    private val objectMapper = jacksonObjectMapper()

    private fun uniqueOrderNo(): String = "ORD-${UUID.randomUUID().toString().take(8)}"

    @Test
    @DisplayName("Should create order and return INIT status")
    fun testCreateOrder() {
        val request =
            CreateOrderRequest(
                orderNo = uniqueOrderNo(),
                productId = "PROD-123",
                productName = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                market = Market.DE,
            )

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.orderNo").value(request.orderNo))
            .andExpect(MockMvcResultMatchers.jsonPath("$.productId").value("PROD-123"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.productName").value("Test Product"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.quantity").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$.amount").value(100.00))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("INIT"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.market").value("DE"))
    }

    @Test
    @DisplayName("Should get order by id")
    fun testGetOrder() {
        val request =
            CreateOrderRequest(
                orderNo = uniqueOrderNo(),
                productId = "PROD-123",
                productName = "Test Product",
                quantity = 2,
                amount = BigDecimal("100.00"),
                market = Market.IT,
            )

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
            .andExpect(MockMvcResultMatchers.jsonPath("$.orderNo").value(request.orderNo))
            .andExpect(MockMvcResultMatchers.jsonPath("$.market").value("IT"))
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

        repeat(3) { i ->
            val request =
                CreateOrderRequest(
                    orderNo = uniqueOrderNo(),
                    productId = "PROD-$i",
                    productName = "Product $i",
                    quantity = i + 1,
                    amount = BigDecimal("${i + 1}0.00"),
                    market = Market.DE,
                )
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

    @Test
    @DisplayName("Should update order status")
    fun testUpdateOrderStatus() {
        val request =
            CreateOrderRequest(
                orderNo = uniqueOrderNo(),
                productId = "PROD-123",
                productName = "Test Product",
                quantity = 1,
                amount = BigDecimal("50.00"),
                market = Market.DE,
            )

        val createResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)),
                ).andExpect(MockMvcResultMatchers.status().isCreated)
                .andReturn()

        val createResponse = objectMapper.readTree(createResult.response.contentAsString)
        val orderId = createResponse.get("id").asLong()

        val order = orderRepository.findById(orderId)
        order?.updateStatus(OrderStatus.LOCAL_INITIALIZED)
        orderRepository.save(requireNotNull(order))

        val updatedOrder = orderRepository.findById(orderId)
        assert(updatedOrder?.status == OrderStatus.LOCAL_INITIALIZED)
    }
}

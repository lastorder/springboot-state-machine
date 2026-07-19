package com.example.statemachine.integration

import com.example.statemachine.application.barrier.CdoaAcceptBarrier
import com.example.statemachine.application.barrier.PurchaseRequestAcceptBarrier
import com.example.statemachine.application.service.OrderCommandService
import com.example.statemachine.domain.enums.Market
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.infrastructure.kafka.KafkaTopics
import com.example.statemachine.infrastructure.persistence.repository.BarrierAggregateJpaRepository
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import java.time.Duration
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(IntegrationTestConfig::class)
@ActiveProfiles("integration")
class OrderFullFlowIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderJpaRepository: OrderJpaRepository

    @Autowired
    private lateinit var barrierAggregateJpaRepository: BarrierAggregateJpaRepository

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    private lateinit var orderCommandService: OrderCommandService

    private val objectMapper = jacksonObjectMapper()

    private fun uniqueOrderNo(): String = "ORD-${UUID.randomUUID().toString().take(8)}"

    @Nested
    @DisplayName("DE Market Flow")
    inner class DeMarketFlow {
        @Test
        @DisplayName("Should complete full flow: PR_APPROVED -> VOM/DOM -> PR_ACCEPT -> CDOA_ACCEPT")
        fun testFullFlowDeMarket() {
            val orderNo = uniqueOrderNo()

            // Step 1: Send PR_APPROVED event
            val prApprovedEvent =
                mapOf(
                    "orderId" to 0,
                    "orderNo" to orderNo,
                    "productId" to "PROD-001",
                    "productName" to "Test Product",
                    "quantity" to 10,
                    "amount" to 100.0,
                    "market" to "DE",
                )
            kafkaTemplate.send(ProducerRecord(KafkaTopics.PR_APPROVED, orderNo, prApprovedEvent))

            // Wait for order created and barrier aggregate initialized (action executed)
            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertNotNull(order)
                    val barriers = barrierAggregateJpaRepository.findAll()
                    assertTrue(barriers.any { it.aggregateKey == orderNo })
                }

            // Step 2: Send VOM success
            val vomEvent = mapOf("orderNo" to orderNo, "success" to true)
            kafkaTemplate.send(ProducerRecord(KafkaTopics.FACTORY_VOM, orderNo, vomEvent))

            // Step 3: Send DOM success -> should trigger ORDER_INITIALIZE_SUCCEED
            val domEvent = mapOf("orderNo" to orderNo, "success" to true)
            kafkaTemplate.send(ProducerRecord(KafkaTopics.FACTORY_DOM, orderNo, domEvent))

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, order?.status)
                }

            // Step 4: Send PURCHASE_REQUEST_ACCEPT event
            orderCommandService.submitOrderEvent(orderNo, com.example.statemachine.domain.enums.OrderEvent.PURCHASE_REQUEST_ACCEPT)

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, order?.status)
                }

            // Step 5: Send barrier pass events for PR_ACCEPT
            listOf("SVS", "PRICE", "FINANCE").forEach { barrier ->
                val barrierPassEvent =
                    mapOf(
                        "orderNo" to orderNo,
                        "barrierType" to barrier,
                        "flowType" to PurchaseRequestAcceptBarrier.FLOW_TYPE,
                        "success" to true,
                    )
                kafkaTemplate.send(ProducerRecord(KafkaTopics.BARRIER_PASS, orderNo, barrierPassEvent))
            }

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTED, order?.status)
                }

            // Step 6: Send CDOA_ACCEPT event
            orderCommandService.submitOrderEvent(orderNo, com.example.statemachine.domain.enums.OrderEvent.CDOA_ACCEPT)

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.CDOA_ACCEPTING, order?.status)
                }

            // Step 7: Send barrier pass events for CDOA_ACCEPT
            listOf("SVS", "PRICE", "FINANCE").forEach { barrier ->
                val barrierPassEvent =
                    mapOf(
                        "orderNo" to orderNo,
                        "barrierType" to barrier,
                        "flowType" to CdoaAcceptBarrier.FLOW_TYPE,
                        "success" to true,
                    )
                kafkaTemplate.send(ProducerRecord(KafkaTopics.BARRIER_PASS, orderNo, barrierPassEvent))
            }

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.CDOA_ACCEPTED, order?.status)
                }
        }
    }

    @Nested
    @DisplayName("IT Market Flow")
    inner class ItMarketFlow {
        @Test
        @DisplayName("Should complete full flow for IT market with 6 barriers")
        fun testFullFlowItMarket() {
            val orderNo = uniqueOrderNo()

            // Step 1: Send PR_APPROVED event for IT market
            val prApprovedEvent =
                mapOf(
                    "orderId" to 0,
                    "orderNo" to orderNo,
                    "productId" to "PROD-002",
                    "productName" to "IT Product",
                    "quantity" to 5,
                    "amount" to 200.0,
                    "market" to "IT",
                )
            kafkaTemplate.send(ProducerRecord(KafkaTopics.PR_APPROVED, orderNo, prApprovedEvent))

            // Wait for order created and barrier aggregate initialized
            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertNotNull(order)
                    assertEquals(Market.IT, order!!.market)
                    val barriers = barrierAggregateJpaRepository.findAll()
                    assertTrue(barriers.any { it.aggregateKey == orderNo })
                }

            // Step 2: Send VOM + DOM
            kafkaTemplate.send(ProducerRecord(KafkaTopics.FACTORY_VOM, orderNo, mapOf("orderNo" to orderNo, "success" to true)))
            kafkaTemplate.send(ProducerRecord(KafkaTopics.FACTORY_DOM, orderNo, mapOf("orderNo" to orderNo, "success" to true)))

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, order?.status)
                }

            // Step 3: PR_ACCEPT with 6 barriers for IT
            orderCommandService.submitOrderEvent(orderNo, com.example.statemachine.domain.enums.OrderEvent.PURCHASE_REQUEST_ACCEPT)

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, order?.status)
                }

            // Step 4: Send 6 barrier pass events for IT market
            listOf("SVS", "BODYBUILDER", "CONTRACT_ROLES", "PRICING", "PAYMENT_SPLIT", "FINANCING_BLUEPRINT").forEach { barrier ->
                val barrierPassEvent =
                    mapOf(
                        "orderNo" to orderNo,
                        "barrierType" to barrier,
                        "flowType" to PurchaseRequestAcceptBarrier.FLOW_TYPE,
                        "success" to true,
                    )
                kafkaTemplate.send(ProducerRecord(KafkaTopics.BARRIER_PASS, orderNo, barrierPassEvent))
            }

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTED, order?.status)
                }

            // Step 5: CDOA_ACCEPT
            orderCommandService.submitOrderEvent(orderNo, com.example.statemachine.domain.enums.OrderEvent.CDOA_ACCEPT)

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.CDOA_ACCEPTING, order?.status)
                }

            // Step 6: Send 6 barriers for CDOA
            listOf("SVS", "BODYBUILDER", "CONTRACT_ROLES", "PRICING", "PAYMENT_SPLIT", "FINANCING_BLUEPRINT").forEach { barrier ->
                val barrierPassEvent =
                    mapOf(
                        "orderNo" to orderNo,
                        "barrierType" to barrier,
                        "flowType" to CdoaAcceptBarrier.FLOW_TYPE,
                        "success" to true,
                    )
                kafkaTemplate.send(ProducerRecord(KafkaTopics.BARRIER_PASS, orderNo, barrierPassEvent))
            }

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.CDOA_ACCEPTED, order?.status)
                }
        }
    }

    @Nested
    @DisplayName("Failure Flow")
    inner class FailureFlow {
        @Test
        @DisplayName("Should handle VOM_FAILED event and transition to ORDER_INITIALIZE_FAILED")
        fun testVomFailedFlow() {
            val orderNo = uniqueOrderNo()

            // Send PR_APPROVED
            val prApprovedEvent =
                mapOf(
                    "orderId" to 0,
                    "orderNo" to orderNo,
                    "productId" to "PROD-003",
                    "productName" to "Test Product",
                    "quantity" to 1,
                    "amount" to 50.0,
                    "market" to "DE",
                )
            kafkaTemplate.send(ProducerRecord(KafkaTopics.PR_APPROVED, orderNo, prApprovedEvent))

            // Wait for order created and barrier aggregate initialized (action executed)
            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertNotNull(order)
                    // Verify barrier aggregate created by SendCoeAction
                    val barriers = barrierAggregateJpaRepository.findAll()
                    assertTrue(barriers.any { it.aggregateKey == orderNo })
                }

            // Send VOM_FAILED
            kafkaTemplate.send(ProducerRecord(KafkaTopics.FACTORY_VOM_FAILED, orderNo, mapOf("orderNo" to orderNo)))

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.ORDER_INITIALIZE_FAILED, order?.status)
                }
        }

        @Test
        @DisplayName("Should handle barrier failure and transition to FAILED state")
        fun testBarrierFailureFlow() {
            val orderNo = uniqueOrderNo()

            // Setup: Get to ORDER_INITIALIZE_SUCCEED
            val prApprovedEvent =
                mapOf(
                    "orderId" to 0,
                    "orderNo" to orderNo,
                    "productId" to "PROD-004",
                    "productName" to "Test Product",
                    "quantity" to 1,
                    "amount" to 50.0,
                    "market" to "DE",
                )
            kafkaTemplate.send(ProducerRecord(KafkaTopics.PR_APPROVED, orderNo, prApprovedEvent))

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    assertNotNull(orderJpaRepository.findByOrderNo(orderNo))
                }

            kafkaTemplate.send(ProducerRecord(KafkaTopics.FACTORY_VOM, orderNo, mapOf("orderNo" to orderNo, "success" to true)))
            kafkaTemplate.send(ProducerRecord(KafkaTopics.FACTORY_DOM, orderNo, mapOf("orderNo" to orderNo, "success" to true)))

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, orderJpaRepository.findByOrderNo(orderNo)?.status)
                }

            // Send PURCHASE_REQUEST_ACCEPT
            orderCommandService.submitOrderEvent(orderNo, com.example.statemachine.domain.enums.OrderEvent.PURCHASE_REQUEST_ACCEPT)

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, orderJpaRepository.findByOrderNo(orderNo)?.status)
                }

            // Send barrier failure
            val barrierFailEvent =
                mapOf(
                    "orderNo" to orderNo,
                    "barrierType" to "SVS",
                    "flowType" to PurchaseRequestAcceptBarrier.FLOW_TYPE,
                    "success" to false,
                )
            kafkaTemplate.send(ProducerRecord(KafkaTopics.BARRIER_PASS, orderNo, barrierFailEvent))

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    val order = orderJpaRepository.findByOrderNo(orderNo)
                    assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED, order?.status)
                }
        }
    }

    @Nested
    @DisplayName("Retry Flow")
    inner class RetryFlow {
        @Test
        @DisplayName("Should retry from PURCHASE_REQUEST_ACCEPT_FAILED")
        fun testRetryFromFailedState() {
            val orderNo = uniqueOrderNo()

            // Setup: Get to FAILED state
            val prApprovedEvent =
                mapOf(
                    "orderId" to 0,
                    "orderNo" to orderNo,
                    "productId" to "PROD-005",
                    "productName" to "Test Product",
                    "quantity" to 1,
                    "amount" to 50.0,
                    "market" to "DE",
                )
            kafkaTemplate.send(ProducerRecord(KafkaTopics.PR_APPROVED, orderNo, prApprovedEvent))

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    assertNotNull(orderJpaRepository.findByOrderNo(orderNo))
                }

            kafkaTemplate.send(ProducerRecord(KafkaTopics.FACTORY_VOM, orderNo, mapOf("orderNo" to orderNo, "success" to true)))
            kafkaTemplate.send(ProducerRecord(KafkaTopics.FACTORY_DOM, orderNo, mapOf("orderNo" to orderNo, "success" to true)))

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, orderJpaRepository.findByOrderNo(orderNo)?.status)
                }

            orderCommandService.submitOrderEvent(orderNo, com.example.statemachine.domain.enums.OrderEvent.PURCHASE_REQUEST_ACCEPT)

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, orderJpaRepository.findByOrderNo(orderNo)?.status)
                }

            // Fail
            kafkaTemplate.send(
                ProducerRecord(
                    KafkaTopics.BARRIER_PASS,
                    orderNo,
                    mapOf(
                        "orderNo" to orderNo,
                        "barrierType" to "SVS",
                        "flowType" to PurchaseRequestAcceptBarrier.FLOW_TYPE,
                        "success" to false,
                    ),
                ),
            )

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED, orderJpaRepository.findByOrderNo(orderNo)?.status)
                }

            // Retry
            orderCommandService.submitOrderEvent(orderNo, com.example.statemachine.domain.enums.OrderEvent.PURCHASE_REQUEST_ACCEPT_RETRY)

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, orderJpaRepository.findByOrderNo(orderNo)?.status)
                }

            // Complete barriers
            listOf("SVS", "PRICE", "FINANCE").forEach { barrier ->
                kafkaTemplate.send(
                    ProducerRecord(
                        KafkaTopics.BARRIER_PASS,
                        orderNo,
                        mapOf(
                            "orderNo" to orderNo,
                            "barrierType" to barrier,
                            "flowType" to PurchaseRequestAcceptBarrier.FLOW_TYPE,
                            "success" to true,
                        ),
                    ),
                )
            }

            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted {
                    assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTED, orderJpaRepository.findByOrderNo(orderNo)?.status)
                }
        }
    }
}

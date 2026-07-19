package com.example.statemachine

import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.api.StateContext
import com.example.statemachine.core.StateMachineFactory
import com.example.statemachine.core.stateMachineFactory
import com.example.statemachine.persistence.InMemoryStateMachineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OrderStateMachineIntegrationTest {
    enum class OrderStatus {
        INIT,
        LOCAL_INITIALIZED,
        FACTORY_ORDER_SUBMITTED,
        ORDER_INITIALIZE_SUCCEED,
        ORDER_INITIALIZE_FAILED,
        PURCHASE_REQUEST_ACCEPTING,
        PURCHASE_REQUEST_ACCEPTED,
        PURCHASE_REQUEST_ACCEPT_FAILED,
        CDOA_ACCEPTING,
        CDOA_ACCEPTED,
        CDOA_ACCEPT_FAILED,
    }

    enum class OrderEvent {
        PR_APPROVED,
        VOM,
        DOM,
        VOM_FAILED,
        PURCHASE_REQUEST_ACCEPT,
        PURCHASE_REQUEST_ACCEPT_SUCCESS,
        PURCHASE_REQUEST_ACCEPT_FAILED,
        PURCHASE_REQUEST_ACCEPT_RETRY,
        CDOA_ACCEPT,
        CDOA_ACCEPT_SUCCESS,
        CDOA_ACCEPT_FAILED,
    }

    data class OrderData(
        val orderNo: String,
        val market: String,
        var status: OrderStatus,
    )

    private lateinit var repository: InMemoryStateMachineRepository<OrderStatus>
    private lateinit var factory: StateMachineFactory<OrderStatus>
    private lateinit var orders: MutableMap<String, OrderData>
    private lateinit var barriers: MutableMap<String, Set<String>>

    @BeforeEach
    fun setUp() {
        repository = InMemoryStateMachineRepository()
        orders = mutableMapOf()
        barriers = mutableMapOf()
        factory = createOrderStateMachine()
    }

    @Nested
    @DisplayName("Full Order Lifecycle - DE Market")
    inner class FullOrderLifecycleDE {
        @Test
        fun `should complete full DE market order flow`() {
            val orderNo = "ORD-DE-001"

            factory.create(orderNo).sendEvent(OrderEvent.PR_APPROVED, mapOf("market" to "DE"))
            assertEquals(OrderStatus.FACTORY_ORDER_SUBMITTED, factory.getState(orderNo))

            factory.create(orderNo).sendEvent(OrderEvent.VOM)
            factory.create(orderNo).sendEvent(OrderEvent.DOM)
            assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, factory.getState(orderNo))

            factory.create(orderNo).sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, factory.getState(orderNo))
            assertEquals(setOf("SVS", "PRICE", "FINANCE"), barriers[orderNo])

            passBarriers(orderNo, setOf("SVS", "PRICE", "FINANCE"))
            factory.create(orderNo).sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTED, factory.getState(orderNo))

            factory.create(orderNo).sendEvent(OrderEvent.CDOA_ACCEPT)
            assertEquals(OrderStatus.CDOA_ACCEPTING, factory.getState(orderNo))

            passBarriers(orderNo, setOf("SVS", "PRICE", "FINANCE"))
            factory.create(orderNo).sendEvent(OrderEvent.CDOA_ACCEPT_SUCCESS)
            assertEquals(OrderStatus.CDOA_ACCEPTED, factory.getState(orderNo))
        }

        @Test
        fun `should handle VOM_FAILED scenario`() {
            val orderNo = "ORD-DE-002"

            factory.create(orderNo).sendEvent(OrderEvent.PR_APPROVED, mapOf("market" to "DE"))
            assertEquals(OrderStatus.FACTORY_ORDER_SUBMITTED, factory.getState(orderNo))

            factory.create(orderNo).sendEvent(OrderEvent.VOM_FAILED)
            assertEquals(OrderStatus.ORDER_INITIALIZE_FAILED, factory.getState(orderNo))

            assertFalse(factory.create(orderNo).sendEvent(OrderEvent.VOM))
            assertEquals(OrderStatus.ORDER_INITIALIZE_FAILED, factory.getState(orderNo))
        }

        @Test
        fun `should handle PR_ACCEPT failed and retry`() {
            val orderNo = "ORD-DE-003"

            factory.create(orderNo).sendEvent(OrderEvent.PR_APPROVED, mapOf("market" to "DE"))
            factory.create(orderNo).sendEvent(OrderEvent.VOM)
            factory.create(orderNo).sendEvent(OrderEvent.DOM)
            assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, factory.getState(orderNo))

            factory.create(orderNo).sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, factory.getState(orderNo))

            factory.create(orderNo).sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT_FAILED)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED, factory.getState(orderNo))

            assertTrue(factory.create(orderNo).sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT_RETRY))
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, factory.getState(orderNo))
        }
    }

    @Nested
    @DisplayName("IT Market with 6 barriers")
    inner class ITMarketTests {
        @Test
        fun `should handle IT market with 6 barriers`() {
            val orderNo = "ORD-IT-001"

            factory.create(orderNo).sendEvent(OrderEvent.PR_APPROVED, mapOf("market" to "IT"))
            assertEquals(OrderStatus.FACTORY_ORDER_SUBMITTED, factory.getState(orderNo))

            factory.create(orderNo).sendEvent(OrderEvent.VOM)
            factory.create(orderNo).sendEvent(OrderEvent.DOM)
            assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, factory.getState(orderNo))

            factory.create(orderNo).sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, factory.getState(orderNo))

            val itBarriers =
                setOf(
                    "SVS",
                    "BODYBUILDER",
                    "CONTRACT_ROLES",
                    "PRICING",
                    "PAYMENT_SPLIT",
                    "FINANCING_BLUEPRINT",
                )
            assertEquals(itBarriers, barriers[orderNo])

            passBarriers(orderNo, itBarriers)
            factory.create(orderNo).sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTED, factory.getState(orderNo))
        }
    }

    @Nested
    @DisplayName("State Transition Validation")
    inner class StateTransitionValidation {
        @Test
        fun `should reject invalid event for current state`() {
            val orderNo = "ORD-001"

            assertFalse(factory.create(orderNo).sendEvent(OrderEvent.VOM))
            assertEquals(OrderStatus.INIT, factory.getState(orderNo))

            assertFalse(factory.create(orderNo).sendEvent(OrderEvent.CDOA_ACCEPT))
            assertEquals(OrderStatus.INIT, factory.getState(orderNo))
        }

        @Test
        fun `should reject event after terminal state`() {
            val orderNo = "ORD-001"

            factory.create(orderNo).sendEvent(OrderEvent.PR_APPROVED, mapOf("market" to "DE"))
            factory.create(orderNo).sendEvent(OrderEvent.VOM_FAILED)
            assertEquals(OrderStatus.ORDER_INITIALIZE_FAILED, factory.getState(orderNo))

            assertFalse(factory.create(orderNo).sendEvent(OrderEvent.VOM))
            assertFalse(factory.create(orderNo).sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT))
        }
    }

    private fun createOrderStateMachine() =
        stateMachineFactory<OrderStatus> {
            initialState = OrderStatus.INIT
            repository = this@OrderStateMachineIntegrationTest.repository

            listener =
                object : StateChangedListener<OrderStatus> {
                    override fun onStateChanged(context: StateContext<OrderStatus>) {
                        orders[context.machineId]?.status = context.targetState
                    }
                }

            transition {
                from(OrderStatus.INIT)
                to(OrderStatus.LOCAL_INITIALIZED)
                on(OrderEvent.PR_APPROVED)
                action { ctx ->
                    val market = ctx.headers["market"] as? String ?: "DE"
                    orders[ctx.machineId] = OrderData(ctx.machineId, market, OrderStatus.LOCAL_INITIALIZED)
                    ActionResult.success()
                }
            }

            transition {
                from(OrderStatus.LOCAL_INITIALIZED)
                to(OrderStatus.FACTORY_ORDER_SUBMITTED)
                action { ctx ->
                    barriers[ctx.machineId] = setOf("VOM", "DOM")
                    ActionResult.success()
                }
            }

            transition {
                from(OrderStatus.FACTORY_ORDER_SUBMITTED)
                to(OrderStatus.ORDER_INITIALIZE_SUCCEED)
                on(OrderEvent.VOM)
                action { ctx ->
                    val current = barriers[ctx.machineId] ?: emptySet()
                    barriers[ctx.machineId] = current - "VOM"
                    ActionResult.success()
                }
            }

            transition {
                from(OrderStatus.FACTORY_ORDER_SUBMITTED)
                to(OrderStatus.ORDER_INITIALIZE_SUCCEED)
                on(OrderEvent.DOM)
                action { ctx ->
                    val current = barriers[ctx.machineId] ?: emptySet()
                    barriers[ctx.machineId] = current - "DOM"
                    ActionResult.success()
                }
            }

            transition {
                from(OrderStatus.FACTORY_ORDER_SUBMITTED)
                to(OrderStatus.ORDER_INITIALIZE_FAILED)
                on(OrderEvent.VOM_FAILED)
            }

            transition {
                from(OrderStatus.ORDER_INITIALIZE_SUCCEED)
                to(OrderStatus.PURCHASE_REQUEST_ACCEPTING)
                on(OrderEvent.PURCHASE_REQUEST_ACCEPT)
                action { ctx ->
                    val order = orders[ctx.machineId]
                    val marketBarriers =
                        if (order?.market == "IT") {
                            setOf(
                                "SVS",
                                "BODYBUILDER",
                                "CONTRACT_ROLES",
                                "PRICING",
                                "PAYMENT_SPLIT",
                                "FINANCING_BLUEPRINT",
                            )
                        } else {
                            setOf("SVS", "PRICE", "FINANCE")
                        }
                    barriers[ctx.machineId] = marketBarriers
                    ActionResult.success()
                }
            }

            transition {
                from(OrderStatus.PURCHASE_REQUEST_ACCEPTING)
                to(OrderStatus.PURCHASE_REQUEST_ACCEPTED)
                on(OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS)
            }

            transition {
                from(OrderStatus.PURCHASE_REQUEST_ACCEPTING)
                to(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED)
                on(OrderEvent.PURCHASE_REQUEST_ACCEPT_FAILED)
            }

            transition {
                from(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED)
                to(OrderStatus.PURCHASE_REQUEST_ACCEPTING)
                on(OrderEvent.PURCHASE_REQUEST_ACCEPT_RETRY)
                action { ctx ->
                    barriers.remove(ctx.machineId)
                    ActionResult.success()
                }
            }

            transition {
                from(OrderStatus.PURCHASE_REQUEST_ACCEPTED)
                to(OrderStatus.CDOA_ACCEPTING)
                on(OrderEvent.CDOA_ACCEPT)
                action { ctx ->
                    val order = orders[ctx.machineId]
                    val marketBarriers =
                        if (order?.market == "IT") {
                            setOf(
                                "SVS",
                                "BODYBUILDER",
                                "CONTRACT_ROLES",
                                "PRICING",
                                "PAYMENT_SPLIT",
                                "FINANCING_BLUEPRINT",
                            )
                        } else {
                            setOf("SVS", "PRICE", "FINANCE")
                        }
                    barriers[ctx.machineId] = marketBarriers
                    ActionResult.success()
                }
            }

            transition {
                from(OrderStatus.CDOA_ACCEPTING)
                to(OrderStatus.CDOA_ACCEPTED)
                on(OrderEvent.CDOA_ACCEPT_SUCCESS)
            }

            transition {
                from(OrderStatus.CDOA_ACCEPTING)
                to(OrderStatus.CDOA_ACCEPT_FAILED)
                on(OrderEvent.CDOA_ACCEPT_FAILED)
            }
        }

    private fun passBarriers(
        orderNo: String,
        barriersToPass: Set<String>,
    ) {
        val current = barriers[orderNo] ?: emptySet()
        barriers[orderNo] = current - barriersToPass
    }
}

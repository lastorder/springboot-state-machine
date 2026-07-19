package com.example.statemachine

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.Event
import com.example.statemachine.api.State
import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.api.StateContext
import com.example.statemachine.core.stateMachine
import com.example.statemachine.persistence.InMemoryStateMachineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OrderStateMachineIntegrationTest {
    enum class OrderStatus : State {
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

    enum class OrderEvent : Event {
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
    private lateinit var orders: MutableMap<String, OrderData>
    private lateinit var barriers: MutableMap<String, Set<String>>

    @BeforeEach
    fun setUp() {
        repository = InMemoryStateMachineRepository()
        orders = mutableMapOf()
        barriers = mutableMapOf()
    }

    @Nested
    @DisplayName("Full Order Lifecycle - DE Market")
    inner class FullOrderLifecycleDE {
        @Test
        fun `should complete full DE market order flow`() {
            val sm = createOrderStateMachine()

            val orderNo = "ORD-DE-001"

            sm.sendEvent(orderNo, OrderEvent.PR_APPROVED, mapOf("market" to "DE"))
            assertEquals(OrderStatus.FACTORY_ORDER_SUBMITTED, sm.getCurrentState(orderNo))

            sm.sendEvent(orderNo, OrderEvent.VOM)
            sm.sendEvent(orderNo, OrderEvent.DOM)
            assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, sm.getCurrentState(orderNo))

            sm.sendEvent(orderNo, OrderEvent.PURCHASE_REQUEST_ACCEPT)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, sm.getCurrentState(orderNo))
            assertEquals(setOf("SVS", "PRICE", "FINANCE"), barriers[orderNo])

            passBarriers(orderNo, setOf("SVS", "PRICE", "FINANCE"))
            sm.sendEvent(orderNo, OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTED, sm.getCurrentState(orderNo))

            sm.sendEvent(orderNo, OrderEvent.CDOA_ACCEPT)
            assertEquals(OrderStatus.CDOA_ACCEPTING, sm.getCurrentState(orderNo))

            passBarriers(orderNo, setOf("SVS", "PRICE", "FINANCE"))
            sm.sendEvent(orderNo, OrderEvent.CDOA_ACCEPT_SUCCESS)
            assertEquals(OrderStatus.CDOA_ACCEPTED, sm.getCurrentState(orderNo))
        }

        @Test
        fun `should handle VOM_FAILED scenario`() {
            val sm = createOrderStateMachine()

            val orderNo = "ORD-DE-002"

            sm.sendEvent(orderNo, OrderEvent.PR_APPROVED, mapOf("market" to "DE"))
            assertEquals(OrderStatus.FACTORY_ORDER_SUBMITTED, sm.getCurrentState(orderNo))

            sm.sendEvent(orderNo, OrderEvent.VOM_FAILED)
            assertEquals(OrderStatus.ORDER_INITIALIZE_FAILED, sm.getCurrentState(orderNo))

            assertFalse(sm.sendEvent(orderNo, OrderEvent.VOM))
            assertEquals(OrderStatus.ORDER_INITIALIZE_FAILED, sm.getCurrentState(orderNo))
        }

        @Test
        fun `should handle PR_ACCEPT failed and retry`() {
            val sm = createOrderStateMachine()

            val orderNo = "ORD-DE-003"

            sm.sendEvent(orderNo, OrderEvent.PR_APPROVED, mapOf("market" to "DE"))
            sm.sendEvent(orderNo, OrderEvent.VOM)
            sm.sendEvent(orderNo, OrderEvent.DOM)
            assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, sm.getCurrentState(orderNo))

            sm.sendEvent(orderNo, OrderEvent.PURCHASE_REQUEST_ACCEPT)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, sm.getCurrentState(orderNo))

            sm.sendEvent(orderNo, OrderEvent.PURCHASE_REQUEST_ACCEPT_FAILED)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED, sm.getCurrentState(orderNo))

            assertTrue(sm.sendEvent(orderNo, OrderEvent.PURCHASE_REQUEST_ACCEPT_RETRY))
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, sm.getCurrentState(orderNo))
        }
    }

    @Nested
    @DisplayName("IT Market with 6 barriers")
    inner class ITMarketTests {
        @Test
        fun `should handle IT market with 6 barriers`() {
            val sm = createOrderStateMachine()

            val orderNo = "ORD-IT-001"

            sm.sendEvent(orderNo, OrderEvent.PR_APPROVED, mapOf("market" to "IT"))
            assertEquals(OrderStatus.FACTORY_ORDER_SUBMITTED, sm.getCurrentState(orderNo))

            sm.sendEvent(orderNo, OrderEvent.VOM)
            sm.sendEvent(orderNo, OrderEvent.DOM)
            assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, sm.getCurrentState(orderNo))

            sm.sendEvent(orderNo, OrderEvent.PURCHASE_REQUEST_ACCEPT)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, sm.getCurrentState(orderNo))

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
            sm.sendEvent(orderNo, OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTED, sm.getCurrentState(orderNo))
        }
    }

    @Nested
    @DisplayName("State Transition Validation")
    inner class StateTransitionValidation {
        @Test
        fun `should reject invalid event for current state`() {
            val sm = createOrderStateMachine()

            val orderNo = "ORD-001"

            assertFalse(sm.sendEvent(orderNo, OrderEvent.VOM))
            assertEquals(OrderStatus.INIT, sm.getCurrentState(orderNo))

            assertFalse(sm.sendEvent(orderNo, OrderEvent.CDOA_ACCEPT))
            assertEquals(OrderStatus.INIT, sm.getCurrentState(orderNo))
        }

        @Test
        fun `should reject event after terminal state`() {
            val sm = createOrderStateMachine()

            val orderNo = "ORD-001"

            sm.sendEvent(orderNo, OrderEvent.PR_APPROVED, mapOf("market" to "DE"))
            sm.sendEvent(orderNo, OrderEvent.VOM_FAILED)
            assertEquals(OrderStatus.ORDER_INITIALIZE_FAILED, sm.getCurrentState(orderNo))

            assertFalse(sm.sendEvent(orderNo, OrderEvent.VOM))
            assertFalse(sm.sendEvent(orderNo, OrderEvent.PURCHASE_REQUEST_ACCEPT))
        }
    }

    private fun createOrderStateMachine() =
        stateMachine<OrderStatus, OrderEvent> {
            initialState = OrderStatus.INIT
            repository = this@OrderStateMachineIntegrationTest.repository

            listener =
                object : StateChangedListener<OrderStatus, OrderEvent> {
                    override fun onStateChanged(context: StateContext<OrderStatus, OrderEvent>) {
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

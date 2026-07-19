package com.example.statemachine

import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.api.StateContext
import com.example.statemachine.core.StateMachine
import com.example.statemachine.core.Transition
import com.example.statemachine.core.TransitionTable
import com.example.statemachine.core.stateMachineFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Nested
    @DisplayName("Full Order Lifecycle")
    inner class FullOrderLifecycle {
        @Test
        fun `should complete full order flow`() {
            val factory = createOrderStateMachineFactory()
            val sm = factory.create("order-001")

            var result = sm.sendEvent(OrderEvent.PR_APPROVED, mapOf("market" to "DE"))
            assertTrue(result.accepted)
            assertTrue(result.stateChanged())
            assertEquals(OrderStatus.INIT, result.previousState)

            assertEquals(OrderStatus.FACTORY_ORDER_SUBMITTED, sm.state)
            assertEquals(OrderStatus.FACTORY_ORDER_SUBMITTED, result.newState)

            result = sm.sendEvent(OrderEvent.VOM)
            assertTrue(result.accepted)
            assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, sm.state)

            result = sm.sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT)
            assertTrue(result.accepted)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, sm.state)

            result = sm.sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS)
            assertTrue(result.accepted)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTED, sm.state)

            result = sm.sendEvent(OrderEvent.CDOA_ACCEPT)
            assertTrue(result.accepted)
            assertEquals(OrderStatus.CDOA_ACCEPTING, sm.state)

            result = sm.sendEvent(OrderEvent.CDOA_ACCEPT_SUCCESS)
            assertTrue(result.accepted)
            assertEquals(OrderStatus.CDOA_ACCEPTED, sm.state)
        }

        @Test
        fun `should handle VOM_FAILED scenario`() {
            val factory = createOrderStateMachineFactory()
            val sm = factory.create("order-002")

            var result = sm.sendEvent(OrderEvent.PR_APPROVED, mapOf("market" to "DE"))
            assertTrue(result.accepted)
            assertEquals(OrderStatus.FACTORY_ORDER_SUBMITTED, sm.state)

            result = sm.sendEvent(OrderEvent.VOM_FAILED)
            assertTrue(result.accepted)
            assertEquals(OrderStatus.ORDER_INITIALIZE_FAILED, sm.state)

            result = sm.sendEvent(OrderEvent.VOM)
            assertFalse(result.accepted)
            assertEquals(OrderStatus.ORDER_INITIALIZE_FAILED, sm.state)
        }

        @Test
        fun `should handle PR_ACCEPT failed and retry`() {
            val factory = createOrderStateMachineFactory()
            val sm = factory.create("order-003")

            sm.sendEvent(OrderEvent.PR_APPROVED)
            sm.sendEvent(OrderEvent.VOM)

            var result = sm.sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT)
            assertTrue(result.accepted)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, sm.state)

            result = sm.sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT_FAILED)
            assertTrue(result.accepted)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED, sm.state)

            result = sm.sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT_RETRY)
            assertTrue(result.accepted)
            assertEquals(OrderStatus.PURCHASE_REQUEST_ACCEPTING, sm.state)
        }
    }

    @Nested
    @DisplayName("State Transition Validation")
    inner class StateTransitionValidation {
        @Test
        fun `should reject invalid event for current state`() {
            val factory = createOrderStateMachineFactory()
            val sm = factory.create("order-001")

            var result = sm.sendEvent(OrderEvent.VOM)
            assertFalse(result.accepted)
            assertFalse(result.stateChanged())
            assertEquals(OrderStatus.INIT, sm.state)

            result = sm.sendEvent(OrderEvent.CDOA_ACCEPT)
            assertFalse(result.accepted)
            assertEquals(OrderStatus.INIT, sm.state)
        }

        @Test
        fun `should reject event after terminal state`() {
            val factory = createOrderStateMachineFactory()
            val sm = factory.create("order-001")

            sm.sendEvent(OrderEvent.PR_APPROVED)
            sm.sendEvent(OrderEvent.VOM_FAILED)
            assertEquals(OrderStatus.ORDER_INITIALIZE_FAILED, sm.state)

            var result = sm.sendEvent(OrderEvent.VOM)
            assertFalse(result.accepted)

            result = sm.sendEvent(OrderEvent.PURCHASE_REQUEST_ACCEPT)
            assertFalse(result.accepted)
        }
    }

    @Test
    fun `should restore state machine and continue`() {
        val transitionTable = createTransitionTable()

        val sm1 = StateMachine.restore(
            id = "order-001",
            currentState = OrderStatus.INIT,
            initialState = OrderStatus.INIT,
            transitionTable = transitionTable,
        )

        val result1 = sm1.sendEvent(OrderEvent.PR_APPROVED)
        assertTrue(result1.accepted)
        assertEquals(OrderStatus.FACTORY_ORDER_SUBMITTED, sm1.state)

        val sm2 = StateMachine.restore(
            id = "order-001",
            currentState = OrderStatus.FACTORY_ORDER_SUBMITTED,
            initialState = OrderStatus.INIT,
            transitionTable = transitionTable,
        )

        val result2 = sm2.sendEvent(OrderEvent.VOM)
        assertTrue(result2.accepted)
        assertEquals(OrderStatus.ORDER_INITIALIZE_SUCCEED, sm2.state)
    }

    private fun createOrderStateMachineFactory() =
        stateMachineFactory<OrderStatus> {
            initialState = OrderStatus.INIT

            transition {
                from(OrderStatus.INIT)
                to(OrderStatus.LOCAL_INITIALIZED)
                on(OrderEvent.PR_APPROVED)
            }

            transition {
                from(OrderStatus.LOCAL_INITIALIZED)
                to(OrderStatus.FACTORY_ORDER_SUBMITTED)
            }

            transition {
                from(OrderStatus.FACTORY_ORDER_SUBMITTED)
                to(OrderStatus.ORDER_INITIALIZE_SUCCEED)
                on(OrderEvent.VOM)
            }

            transition {
                from(OrderStatus.FACTORY_ORDER_SUBMITTED)
                to(OrderStatus.ORDER_INITIALIZE_SUCCEED)
                on(OrderEvent.DOM)
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
            }

            transition {
                from(OrderStatus.PURCHASE_REQUEST_ACCEPTED)
                to(OrderStatus.CDOA_ACCEPTING)
                on(OrderEvent.CDOA_ACCEPT)
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

    private fun createTransitionTable(): TransitionTable<OrderStatus> =
        TransitionTable<OrderStatus>().apply {
            add(Transition(OrderStatus.INIT, OrderStatus.LOCAL_INITIALIZED, OrderEvent.PR_APPROVED))
            add(Transition(OrderStatus.LOCAL_INITIALIZED, OrderStatus.FACTORY_ORDER_SUBMITTED, null))
            add(Transition(OrderStatus.FACTORY_ORDER_SUBMITTED, OrderStatus.ORDER_INITIALIZE_SUCCEED, OrderEvent.VOM))
            add(Transition(OrderStatus.FACTORY_ORDER_SUBMITTED, OrderStatus.ORDER_INITIALIZE_SUCCEED, OrderEvent.DOM))
            add(Transition(OrderStatus.FACTORY_ORDER_SUBMITTED, OrderStatus.ORDER_INITIALIZE_FAILED, OrderEvent.VOM_FAILED))
            add(Transition(OrderStatus.ORDER_INITIALIZE_SUCCEED, OrderStatus.PURCHASE_REQUEST_ACCEPTING, OrderEvent.PURCHASE_REQUEST_ACCEPT))
            add(Transition(OrderStatus.PURCHASE_REQUEST_ACCEPTING, OrderStatus.PURCHASE_REQUEST_ACCEPTED, OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS))
            add(Transition(OrderStatus.PURCHASE_REQUEST_ACCEPTING, OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED, OrderEvent.PURCHASE_REQUEST_ACCEPT_FAILED))
            add(Transition(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED, OrderStatus.PURCHASE_REQUEST_ACCEPTING, OrderEvent.PURCHASE_REQUEST_ACCEPT_RETRY))
            add(Transition(OrderStatus.PURCHASE_REQUEST_ACCEPTED, OrderStatus.CDOA_ACCEPTING, OrderEvent.CDOA_ACCEPT))
            add(Transition(OrderStatus.CDOA_ACCEPTING, OrderStatus.CDOA_ACCEPTED, OrderEvent.CDOA_ACCEPT_SUCCESS))
            add(Transition(OrderStatus.CDOA_ACCEPTING, OrderStatus.CDOA_ACCEPT_FAILED, OrderEvent.CDOA_ACCEPT_FAILED))
        }
}

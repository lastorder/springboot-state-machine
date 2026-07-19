package com.example.statemachine

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.api.StateContext
import com.example.statemachine.core.stateMachineFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StateMachineTest {
    enum class TestState {
        INIT,
        S1,
        S2,
        S3,
        END,
    }

    enum class TestEvent {
        E1,
        E2,
        E3,
        AUTO,
    }

    @Nested
    @DisplayName("Basic Transitions")
    inner class BasicTransitions {
        @Test
        fun `should transition on valid event`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            val sm = factory.create("test")
            val result = sm.sendEvent(TestEvent.E1)

            assertTrue(result.accepted)
            assertTrue(result.stateChanged())
            assertEquals(TestState.INIT, result.previousState)
            assertEquals(TestState.S1, result.newState)
            assertEquals(TestState.S1, sm.state)
        }

        @Test
        fun `should reject invalid event`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            val sm = factory.create("test")
            val result = sm.sendEvent(TestEvent.E2)

            assertFalse(result.accepted)
            assertFalse(result.stateChanged())
            assertEquals(TestState.INIT, result.previousState)
            assertEquals(TestState.INIT, result.newState)
            assertEquals(TestEvent.E2, result.event)
        }

        @Test
        fun `should transition through multiple states`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                    transition {
                        from(TestState.S1)
                        to(TestState.S2)
                        on(TestEvent.E2)
                    }
                    transition {
                        from(TestState.S2)
                        to(TestState.END)
                        on(TestEvent.E3)
                    }
                }

            val sm = factory.create("test")

            var result = sm.sendEvent(TestEvent.E1)
            assertTrue(result.accepted)
            assertEquals(TestState.S1, sm.state)

            result = sm.sendEvent(TestEvent.E2)
            assertTrue(result.accepted)
            assertEquals(TestState.S2, sm.state)

            result = sm.sendEvent(TestEvent.E3)
            assertTrue(result.accepted)
            assertEquals(TestState.END, sm.state)
        }
    }

    @Nested
    @DisplayName("Action Execution")
    inner class ActionExecution {
        @Test
        fun `should execute action on transition`() {
            var executed = false
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action { ctx ->
                            executed = true
                            ActionResult.success()
                        }
                    }
                }

            val sm = factory.create("test")
            assertTrue(sm.sendEvent(TestEvent.E1).accepted)
            assertTrue(executed)
        }

        @Test
        fun `should pass correct context to action`() {
            var receivedMachineId: String? = null
            var receivedSourceState: TestState? = null
            var receivedTargetState: TestState? = null

            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action { ctx ->
                            receivedMachineId = ctx.machineId
                            receivedSourceState = ctx.sourceState
                            receivedTargetState = ctx.targetState
                            ActionResult.success()
                        }
                    }
                }

            factory.create("order-123").sendEvent(TestEvent.E1)

            assertEquals("order-123", receivedMachineId)
            assertEquals(TestState.INIT, receivedSourceState)
            assertEquals(TestState.S1, receivedTargetState)
        }

        @Test
        fun `should reject transition when action fails`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action { ActionResult.businessError("Action failed") }
                    }
                }

            val sm = factory.create("test")
            val result = sm.sendEvent(TestEvent.E1)

            assertFalse(result.accepted)
            assertEquals(TestState.INIT, sm.state)
        }

        @Test
        fun `should support action as interface implementation`() {
            class TestAction : Action<TestState> {
                var executed = false

                override fun execute(context: StateContext<TestState>): ActionResult {
                    executed = true
                    return ActionResult.success()
                }
            }

            val action = TestAction()
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action(action)
                    }
                }

            factory.create("test").sendEvent(TestEvent.E1)

            assertTrue(action.executed)
        }
    }

    @Nested
    @DisplayName("Auto Transitions")
    inner class AutoTransitions {
        @Test
        fun `should execute auto transition after state change`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                    transition {
                        from(TestState.S1)
                        to(TestState.S2)
                    }
                }

            val sm = factory.create("test")
            sm.sendEvent(TestEvent.E1)

            assertEquals(TestState.S2, sm.state)
        }

        @Test
        fun `should execute chained auto transitions`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                    transition {
                        from(TestState.S1)
                        to(TestState.S2)
                    }
                    transition {
                        from(TestState.S2)
                        to(TestState.S3)
                    }
                    transition {
                        from(TestState.S3)
                        to(TestState.END)
                    }
                }

            val sm = factory.create("test")
            sm.sendEvent(TestEvent.E1)

            assertEquals(TestState.END, sm.state)
        }

        @Test
        fun `should stop auto transition chain when action fails`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                    transition {
                        from(TestState.S1)
                        to(TestState.S2)
                        action { ActionResult.businessError("Stop here") }
                    }
                    transition {
                        from(TestState.S2)
                        to(TestState.END)
                    }
                }

            val sm = factory.create("test")
            sm.sendEvent(TestEvent.E1)

            assertEquals(TestState.S1, sm.state)
        }
    }

    @Nested
    @DisplayName("Listener")
    inner class ListenerTests {
        @Test
        fun `should call listener on successful transition`() {
            var listenerCalled = false
            var receivedContext: StateContext<TestState>? = null

            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    listener =
                        object : StateChangedListener<TestState> {
                            override fun onStateChanged(context: StateContext<TestState>) {
                                listenerCalled = true
                                receivedContext = context
                            }
                        }
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            factory.create("test").sendEvent(TestEvent.E1)

            assertTrue(listenerCalled)
            assertEquals(TestState.INIT, receivedContext?.sourceState)
            assertEquals(TestState.S1, receivedContext?.targetState)
        }

        @Test
        fun `should NOT call listener when action fails`() {
            var listenerCalled = false

            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    listener =
                        object : StateChangedListener<TestState> {
                            override fun onStateChanged(context: StateContext<TestState>) {
                                listenerCalled = true
                            }
                        }
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action { ActionResult.businessError("Failed") }
                    }
                }

            factory.create("test").sendEvent(TestEvent.E1)

            assertFalse(listenerCalled)
        }

        @Test
        fun `should call listener for each auto transition`() {
            val stateChanges = mutableListOf<Pair<TestState, TestState>>()

            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    listener =
                        object : StateChangedListener<TestState> {
                            override fun onStateChanged(context: StateContext<TestState>) {
                                stateChanges.add(context.sourceState to context.targetState)
                            }
                        }
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                    transition {
                        from(TestState.S1)
                        to(TestState.S2)
                    }
                }

            factory.create("test").sendEvent(TestEvent.E1)

            assertEquals(2, stateChanges.size)
            assertEquals(TestState.INIT to TestState.S1, stateChanges[0])
            assertEquals(TestState.S1 to TestState.S2, stateChanges[1])
        }
    }

    @Nested
    @DisplayName("Headers and Extended State")
    inner class HeadersAndExtendedStateTests {
        @Test
        fun `should pass headers to action`() {
            var receivedHeaders: Map<String, Any?>? = null

            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action { ctx ->
                            receivedHeaders = ctx.headers
                            ActionResult.success()
                        }
                    }
                }

            factory.create("test").sendEvent(TestEvent.E1, mapOf("orderId" to 123, "status" to "PENDING"))

            assertEquals(mapOf("orderId" to 123, "status" to "PENDING"), receivedHeaders)
        }

        @Test
        fun `should support extended state for passing data between actions`() {
            var valueInSecondAction: Any? = null

            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action { ctx ->
                            ctx.extendedState["key"] = "value"
                            ActionResult.success()
                        }
                    }
                    transition {
                        from(TestState.S1)
                        to(TestState.S2)
                        action { ctx ->
                            valueInSecondAction = ctx.extendedState["key"]
                            ActionResult.success()
                        }
                    }
                }

            factory.create("test").sendEvent(TestEvent.E1)

            assertEquals("value", valueInSecondAction)
        }
    }

    @Nested
    @DisplayName("State Restoration")
    inner class StateRestorationTests {
        @Test
        fun `should restore state machine with given state`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.S1)
                        to(TestState.S2)
                        on(TestEvent.E2)
                    }
                }

            val sm = factory.create("test")

            val restored =
                com.example.statemachine.core.StateMachine.restore(
                    id = "test",
                    currentState = TestState.S1,
                    initialState = TestState.INIT,
                )

            assertEquals(TestState.S1, restored.state)
        }

        @Test
        fun `should continue from restored state`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                    transition {
                        from(TestState.S1)
                        to(TestState.S2)
                        on(TestEvent.E2)
                    }
                }

            factory.create("test").sendEvent(TestEvent.E1)

            val transitionTable =
                com.example.statemachine.core.TransitionTable<TestState>().apply {
                    add(
                        com.example.statemachine.core.Transition(
                            source = TestState.S1,
                            target = TestState.S2,
                            event = TestEvent.E2,
                        ),
                    )
                }

            val restored =
                com.example.statemachine.core.StateMachine.restore(
                    id = "test",
                    currentState = TestState.S1,
                    initialState = TestState.INIT,
                    transitionTable = transitionTable,
                )

            val result = restored.sendEvent(TestEvent.E2)
            assertTrue(result.accepted)
            assertEquals(TestState.S2, restored.state)
        }
    }
}

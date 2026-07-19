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

class StateMachineTest {
    enum class TestState : State {
        INIT,
        S1,
        S2,
        S3,
        END,
    }

    enum class TestEvent : Event {
        E1,
        E2,
        E3,
        AUTO,
    }

    private lateinit var repository: InMemoryStateMachineRepository<TestState>

    @BeforeEach
    fun setUp() {
        repository = InMemoryStateMachineRepository()
    }

    @Nested
    @DisplayName("Basic Transitions")
    inner class BasicTransitions {
        @Test
        fun `should transition on valid event`() {
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            val result = sm.sendEvent("test", TestEvent.E1)

            assertTrue(result)
            assertEquals(TestState.S1, sm.getCurrentState("test"))
        }

        @Test
        fun `should reject invalid event`() {
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            val result = sm.sendEvent("test", TestEvent.E2)

            assertFalse(result)
            assertEquals(TestState.INIT, sm.getCurrentState("test"))
        }

        @Test
        fun `should transition through multiple states`() {
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
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

            assertTrue(sm.sendEvent("test", TestEvent.E1))
            assertEquals(TestState.S1, sm.getCurrentState("test"))

            assertTrue(sm.sendEvent("test", TestEvent.E2))
            assertEquals(TestState.S2, sm.getCurrentState("test"))

            assertTrue(sm.sendEvent("test", TestEvent.E3))
            assertEquals(TestState.END, sm.getCurrentState("test"))
        }
    }

    @Nested
    @DisplayName("Action Execution")
    inner class ActionExecution {
        @Test
        fun `should execute action on transition`() {
            var executed = false
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
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

            assertTrue(sm.sendEvent("test", TestEvent.E1))
            assertTrue(executed)
        }

        @Test
        fun `should pass correct context to action`() {
            var receivedMachineId: String? = null
            var receivedSourceState: TestState? = null
            var receivedTargetState: TestState? = null

            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
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

            sm.sendEvent("order-123", TestEvent.E1)

            assertEquals("order-123", receivedMachineId)
            assertEquals(TestState.INIT, receivedSourceState)
            assertEquals(TestState.S1, receivedTargetState)
        }

        @Test
        fun `should reject transition when action fails`() {
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action { ActionResult.failure("Action failed") }
                    }
                }

            val result = sm.sendEvent("test", TestEvent.E1)

            assertFalse(result)
            assertEquals(TestState.INIT, sm.getCurrentState("test"))
        }

        @Test
        fun `should support action as interface implementation`() {
            class TestAction : Action<TestState, TestEvent> {
                var executed = false

                override fun execute(context: StateContext<TestState, TestEvent>): ActionResult<TestEvent> {
                    executed = true
                    return ActionResult.success()
                }
            }

            val action = TestAction()
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action(action)
                    }
                }

            sm.sendEvent("test", TestEvent.E1)

            assertTrue(action.executed)
        }
    }

    @Nested
    @DisplayName("Auto Transitions")
    inner class AutoTransitions {
        @Test
        fun `should execute auto transition after state change`() {
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
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

            sm.sendEvent("test", TestEvent.E1)

            assertEquals(TestState.S2, sm.getCurrentState("test"))
        }

        @Test
        fun `should execute chained auto transitions`() {
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
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

            sm.sendEvent("test", TestEvent.E1)

            assertEquals(TestState.END, sm.getCurrentState("test"))
        }

        @Test
        fun `should stop auto transition chain when action fails`() {
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                    transition {
                        from(TestState.S1)
                        to(TestState.S2)
                        action { ActionResult.failure("Stop here") }
                    }
                    transition {
                        from(TestState.S2)
                        to(TestState.END)
                    }
                }

            sm.sendEvent("test", TestEvent.E1)

            assertEquals(TestState.S1, sm.getCurrentState("test"))
        }
    }

    @Nested
    @DisplayName("Listener")
    inner class ListenerTests {
        @Test
        fun `should call listener on successful transition`() {
            var listenerCalled = false
            var receivedContext: StateContext<TestState, TestEvent>? = null

            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    listener =
                        object : StateChangedListener<TestState, TestEvent> {
                            override fun onStateChanged(context: StateContext<TestState, TestEvent>) {
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

            sm.sendEvent("test", TestEvent.E1)

            assertTrue(listenerCalled)
            assertEquals(TestState.INIT, receivedContext?.sourceState)
            assertEquals(TestState.S1, receivedContext?.targetState)
        }

        @Test
        fun `should NOT call listener when action fails`() {
            var listenerCalled = false

            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    listener =
                        object : StateChangedListener<TestState, TestEvent> {
                            override fun onStateChanged(context: StateContext<TestState, TestEvent>) {
                                listenerCalled = true
                            }
                        }
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action { ActionResult.failure("Failed") }
                    }
                }

            sm.sendEvent("test", TestEvent.E1)

            assertFalse(listenerCalled)
        }

        @Test
        fun `should call listener for each auto transition`() {
            val stateChanges = mutableListOf<Pair<TestState, TestState>>()

            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    listener =
                        object : StateChangedListener<TestState, TestEvent> {
                            override fun onStateChanged(context: StateContext<TestState, TestEvent>) {
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

            sm.sendEvent("test", TestEvent.E1)

            assertEquals(2, stateChanges.size)
            assertEquals(TestState.INIT to TestState.S1, stateChanges[0])
            assertEquals(TestState.S1 to TestState.S2, stateChanges[1])
        }
    }

    @Nested
    @DisplayName("Persistence")
    inner class PersistenceTests {
        @Test
        fun `should restore state from repository`() {
            repository.save("existing", TestState.S2)

            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.S2)
                        to(TestState.S3)
                        on(TestEvent.E3)
                    }
                }

            assertEquals(TestState.S2, sm.getCurrentState("existing"))
            assertTrue(sm.sendEvent("existing", TestEvent.E3))
            assertEquals(TestState.S3, sm.getCurrentState("existing"))
        }

        @Test
        fun `should use initial state when not in repository`() {
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            assertEquals(TestState.INIT, sm.getCurrentState("new-machine"))
        }

        @Test
        fun `should handle multiple machines independently`() {
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
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

            sm.sendEvent("machine-1", TestEvent.E1)
            sm.sendEvent("machine-2", TestEvent.E1)
            sm.sendEvent("machine-2", TestEvent.E2)

            assertEquals(TestState.S1, sm.getCurrentState("machine-1"))
            assertEquals(TestState.S2, sm.getCurrentState("machine-2"))
        }
    }

    @Nested
    @DisplayName("Headers and Extended State")
    inner class HeadersAndExtendedStateTests {
        @Test
        fun `should pass headers to action`() {
            var receivedHeaders: Map<String, Any?>? = null

            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
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

            sm.sendEvent("test", TestEvent.E1, mapOf("orderId" to 123, "status" to "PENDING"))

            assertEquals(mapOf("orderId" to 123, "status" to "PENDING"), receivedHeaders)
        }

        @Test
        fun `should support extended state for passing data between actions`() {
            var valueInSecondAction: Any? = null

            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
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

            sm.sendEvent("test", TestEvent.E1)

            assertEquals("value", valueInSecondAction)
        }
    }

    @Nested
    @DisplayName("Reset")
    inner class ResetTests {
        @Test
        fun `should reset state machine to initial state`() {
            val sm =
                stateMachine<TestState, TestEvent> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            sm.sendEvent("test", TestEvent.E1)
            assertEquals(TestState.S1, sm.getCurrentState("test"))

            sm.reset("test")
            assertEquals(TestState.INIT, sm.getCurrentState("test"))
        }
    }
}

package com.example.statemachine

import com.example.statemachine.api.Action
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
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            val sm = factory.create("test")
            val result = sm.sendEvent(TestEvent.E1)

            assertTrue(result)
            assertEquals(TestState.S1, sm.state)
        }

        @Test
        fun `should reject invalid event`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            val sm = factory.create("test")
            val result = sm.sendEvent(TestEvent.E2)

            assertFalse(result)
            assertEquals(TestState.INIT, sm.state)
        }

        @Test
        fun `should transition through multiple states`() {
            val factory =
                stateMachineFactory<TestState> {
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

            val sm = factory.create("test")

            assertTrue(sm.sendEvent(TestEvent.E1))
            assertEquals(TestState.S1, sm.state)

            assertTrue(sm.sendEvent(TestEvent.E2))
            assertEquals(TestState.S2, sm.state)

            assertTrue(sm.sendEvent(TestEvent.E3))
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

            val sm = factory.create("test")
            assertTrue(sm.sendEvent(TestEvent.E1))
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
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                        action { ActionResult.failure("Action failed") }
                    }
                }

            val sm = factory.create("test")
            val result = sm.sendEvent(TestEvent.E1)

            assertFalse(result)
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
                    repository = this@StateMachineTest.repository
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

            val sm = factory.create("test")
            sm.sendEvent(TestEvent.E1)

            assertEquals(TestState.S2, sm.state)
        }

        @Test
        fun `should execute chained auto transitions`() {
            val factory =
                stateMachineFactory<TestState> {
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

            val sm = factory.create("test")
            sm.sendEvent(TestEvent.E1)

            assertEquals(TestState.END, sm.state)
        }

        @Test
        fun `should stop auto transition chain when action fails`() {
            val factory =
                stateMachineFactory<TestState> {
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
                    repository = this@StateMachineTest.repository
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
                    repository = this@StateMachineTest.repository
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
                        action { ActionResult.failure("Failed") }
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
                    repository = this@StateMachineTest.repository
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
    @DisplayName("Persistence")
    inner class PersistenceTests {
        @Test
        fun `should restore state from repository`() {
            val sm = factory.create("existing")
            sm.sendEvent(TestEvent.E1)
            assertEquals(TestState.S1, sm.state)

            val restored = factory.create("existing")
            assertEquals(TestState.S1, restored.state)

            restored.sendEvent(TestEvent.E2)
            assertEquals(TestState.S2, restored.state)
        }

        @Test
        fun `should use initial state when not in repository`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            val sm = factory.create("new-machine")
            assertEquals(TestState.INIT, sm.state)
        }

        @Test
        fun `should handle multiple machines independently`() {
            val factory =
                stateMachineFactory<TestState> {
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

            factory.create("machine-1").sendEvent(TestEvent.E1)
            factory.create("machine-2").sendEvent(TestEvent.E1)
            factory.create("machine-2").sendEvent(TestEvent.E2)

            assertEquals(TestState.S1, factory.getState("machine-1"))
            assertEquals(TestState.S2, factory.getState("machine-2"))
        }

        private val factory: StateMachineFactory<TestState>
            get() =
                stateMachineFactory {
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

            factory.create("test").sendEvent(TestEvent.E1, mapOf("orderId" to 123, "status" to "PENDING"))

            assertEquals(mapOf("orderId" to 123, "status" to "PENDING"), receivedHeaders)
        }

        @Test
        fun `should support extended state for passing data between actions`() {
            var valueInSecondAction: Any? = null

            val factory =
                stateMachineFactory<TestState> {
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

            factory.create("test").sendEvent(TestEvent.E1)

            assertEquals("value", valueInSecondAction)
        }
    }

    @Nested
    @DisplayName("Reset")
    inner class ResetTests {
        @Test
        fun `should reset state machine to initial state`() {
            val factory =
                stateMachineFactory<TestState> {
                    initialState = TestState.INIT
                    repository = this@StateMachineTest.repository
                    transition {
                        from(TestState.INIT)
                        to(TestState.S1)
                        on(TestEvent.E1)
                    }
                }

            val sm = factory.create("test")
            sm.sendEvent(TestEvent.E1)
            assertEquals(TestState.S1, sm.state)

            sm.reset()
            assertEquals(TestState.INIT, factory.create("test").state)
        }
    }
}

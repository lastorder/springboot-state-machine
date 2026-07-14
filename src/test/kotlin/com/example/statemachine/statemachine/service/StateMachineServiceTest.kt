package com.example.statemachine.statemachine.service

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.messaging.Message
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.StateMachineEventResult
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.data.jpa.JpaRepositoryStateMachine
import org.springframework.statemachine.data.jpa.JpaStateMachineRepository
import org.springframework.statemachine.state.State
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.Optional

class StateMachineServiceTest {
    private lateinit var stateMachineFactory: StateMachineFactory<OrderStatus, OrderEvent>
    private lateinit var jpaStateMachineRepository: JpaStateMachineRepository
    private lateinit var stateMachine: StateMachine<OrderStatus, OrderEvent>
    private lateinit var stateMachineService: StateMachineService

    @BeforeEach
    fun setUp() {
        stateMachineFactory = mockk()
        jpaStateMachineRepository = mockk()
        stateMachine = mockk(relaxed = true)
        stateMachineService = StateMachineService(stateMachineFactory, jpaStateMachineRepository)
    }

    @Test
    @DisplayName("Should send event successfully")
    fun testSendEventSuccess() {
        val orderId = 1L
        val event = OrderEvent.PR_APPROVED
        val machineId = orderId.toString()

        val eventResult =
            mockk<StateMachineEventResult<OrderStatus, OrderEvent>> {
                every { resultType } returns StateMachineEventResult.ResultType.ACCEPTED
            }

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } returns Optional.empty()
        every { stateMachine.sendEvent(any<Mono<Message<OrderEvent>>>()) } returns Flux.just(eventResult)
        every { stateMachine.state } returns mockState(OrderStatus.INIT)
        every { stateMachine.stopReactively() } returns Mono.empty()
        every { jpaStateMachineRepository.save(any()) } returns JpaRepositoryStateMachine()

        val result = stateMachineService.sendEvent(orderId, event)

        verify { stateMachineFactory.getStateMachine(machineId) }
        assertEquals(true, result)
    }

    @Test
    @DisplayName("Should send event with headers successfully")
    fun testSendEventWithHeaders() {
        val orderId = 1L
        val event = OrderEvent.PR_APPROVED
        val headers = mapOf("orderNo" to "ORD-001")
        val machineId = orderId.toString()

        val eventResult =
            mockk<StateMachineEventResult<OrderStatus, OrderEvent>> {
                every { resultType } returns StateMachineEventResult.ResultType.ACCEPTED
            }

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } returns Optional.empty()
        every { stateMachine.sendEvent(any<Mono<Message<OrderEvent>>>()) } returns Flux.just(eventResult)
        every { stateMachine.state } returns mockState(OrderStatus.LOCAL_INITIALIZED)
        every { stateMachine.stopReactively() } returns Mono.empty()
        every { jpaStateMachineRepository.save(any()) } returns JpaRepositoryStateMachine()

        val result = stateMachineService.sendEvent(orderId, event, headers)

        verify { stateMachineFactory.getStateMachine(machineId) }
        assertEquals(true, result)
    }

    @Test
    @DisplayName("Should get current state successfully")
    fun testGetCurrentState() {
        val orderId = 1L
        val machineId = orderId.toString()
        val entity =
            JpaRepositoryStateMachine().apply {
                this.machineId = machineId
                this.state = OrderStatus.LOCAL_INITIALIZED.name
            }

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } returns Optional.of(entity)
        every { stateMachine.state } returns mockState(OrderStatus.LOCAL_INITIALIZED)
        every { stateMachine.stopReactively() } returns Mono.empty()

        val result = stateMachineService.getCurrentState(orderId)

        assertEquals(OrderStatus.LOCAL_INITIALIZED, result)
    }

    @Test
    @DisplayName("Should return state when no persisted state exists")
    fun testGetCurrentStateNoPersistedState() {
        val orderId = 999L
        val machineId = orderId.toString()

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } returns Optional.empty()
        every { stateMachine.state } returns mockState(OrderStatus.INIT)
        every { stateMachine.stopReactively() } returns Mono.empty()

        val result = stateMachineService.getCurrentState(orderId)

        assertEquals(OrderStatus.INIT, result)
    }

    @Test
    @DisplayName("Should return null when exception occurs getting state")
    fun testGetCurrentStateException() {
        val orderId = 1L
        val machineId = orderId.toString()

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } throws RuntimeException("DB error")

        val result = stateMachineService.getCurrentState(orderId)

        assertNull(result)
    }

    @Test
    @DisplayName("Should initialize state machine")
    fun testInitializeStateMachine() {
        val orderId = 1L
        val machineId = orderId.toString()

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { stateMachine.stateMachineAccessor } returns mockk(relaxed = true)
        every { jpaStateMachineRepository.save(any()) } returns JpaRepositoryStateMachine()
        every { stateMachine.state } returns mockState(OrderStatus.INIT)
        every { stateMachine.stopReactively() } returns Mono.empty()

        stateMachineService.initializeStateMachine(orderId)

        verify { stateMachineFactory.getStateMachine(machineId) }
    }

    @Test
    @DisplayName("Should initialize state machine with custom initial state")
    fun testInitializeStateMachineCustomInitialState() {
        val orderId = 1L
        val machineId = orderId.toString()
        val initialState = OrderStatus.LOCAL_INITIALIZED

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { stateMachine.stateMachineAccessor } returns mockk(relaxed = true)
        every { jpaStateMachineRepository.save(any()) } returns JpaRepositoryStateMachine()
        every { stateMachine.state } returns mockState(initialState)
        every { stateMachine.stopReactively() } returns Mono.empty()

        stateMachineService.initializeStateMachine(orderId, initialState)

        verify { stateMachineFactory.getStateMachine(machineId) }
    }

    @Test
    @DisplayName("Should return false when event send fails with rejected result")
    fun testSendEventRejected() {
        val orderId = 1L
        val event = OrderEvent.VOM
        val machineId = orderId.toString()

        val eventResult =
            mockk<StateMachineEventResult<OrderStatus, OrderEvent>> {
                every { resultType } returns StateMachineEventResult.ResultType.DENIED
            }

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } returns Optional.empty()
        every { stateMachine.sendEvent(any<Mono<Message<OrderEvent>>>()) } returns Flux.just(eventResult)
        every { stateMachine.stopReactively() } returns Mono.empty()

        val result = stateMachineService.sendEvent(orderId, event)

        assertEquals(false, result)
    }

    @Test
    @DisplayName("Should return false on exception")
    fun testSendEventException() {
        val orderId = 1L
        val event = OrderEvent.DOM
        val machineId = orderId.toString()

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } throws RuntimeException("DB error")

        val result = stateMachineService.sendEvent(orderId, event)

        assertEquals(false, result)
    }

    private fun mockState(status: OrderStatus): State<OrderStatus, OrderEvent> =
        mockk {
            every { id } returns status
            every { getPseudoState() } returns null
            every { ids } returns listOf(status)
        }
}

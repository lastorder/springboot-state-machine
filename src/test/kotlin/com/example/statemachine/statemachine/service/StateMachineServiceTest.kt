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
    fun testSendEvent_Success() {
        val orderId = 1L
        val event = OrderEvent.USER_CONFIRM
        val machineId = orderId.toString()

        val eventResult =
            mockk<StateMachineEventResult<OrderStatus, OrderEvent>> {
                every { resultType } returns StateMachineEventResult.ResultType.ACCEPTED
            }

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } returns Optional.empty()
        every { stateMachine.sendEvent(any<Mono<Message<OrderEvent>>>()) } returns Flux.just(eventResult)
        every { stateMachine.state } returns mockState(OrderStatus.PENDING_CONFIRMATION)
        every { stateMachine.stopReactively() } returns Mono.empty()
        every { jpaStateMachineRepository.save(any()) } returns JpaRepositoryStateMachine()

        val result = stateMachineService.sendEvent(orderId, event)

        verify { stateMachineFactory.getStateMachine(machineId) }
        assertEquals(true, result)
    }

    @Test
    @DisplayName("Should send event with headers successfully")
    fun testSendEvent_WithHeaders() {
        val orderId = 1L
        val event = OrderEvent.PAY
        val headers = mapOf("amount" to 100.0)
        val machineId = orderId.toString()

        val eventResult =
            mockk<StateMachineEventResult<OrderStatus, OrderEvent>> {
                every { resultType } returns StateMachineEventResult.ResultType.ACCEPTED
            }

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } returns Optional.empty()
        every { stateMachine.sendEvent(any<Mono<Message<OrderEvent>>>()) } returns Flux.just(eventResult)
        every { stateMachine.state } returns mockState(OrderStatus.PENDING_PAYMENT)
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
                this.state = OrderStatus.PENDING_PAYMENT.name
            }

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } returns Optional.of(entity)
        every { stateMachine.state } returns mockState(OrderStatus.PENDING_PAYMENT)
        every { stateMachine.stopReactively() } returns Mono.empty()

        val result = stateMachineService.getCurrentState(orderId)

        assertEquals(OrderStatus.PENDING_PAYMENT, result)
    }

    @Test
    @DisplayName("Should return state when no persisted state exists")
    fun testGetCurrentState_NoPersistedState() {
        val orderId = 999L
        val machineId = orderId.toString()

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } returns Optional.empty()
        every { stateMachine.state } returns mockState(OrderStatus.CREATED)
        every { stateMachine.stopReactively() } returns Mono.empty()

        val result = stateMachineService.getCurrentState(orderId)

        assertEquals(OrderStatus.CREATED, result)
    }

    @Test
    @DisplayName("Should return null when exception occurs getting state")
    fun testGetCurrentState_Exception() {
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
        every { stateMachine.state } returns mockState(OrderStatus.CREATED)
        every { stateMachine.stopReactively() } returns Mono.empty()

        stateMachineService.initializeStateMachine(orderId)

        verify { stateMachineFactory.getStateMachine(machineId) }
    }

    @Test
    @DisplayName("Should initialize state machine with custom initial state")
    fun testInitializeStateMachine_CustomInitialState() {
        val orderId = 1L
        val machineId = orderId.toString()
        val initialState = OrderStatus.PENDING_PAYMENT

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
    fun testSendEvent_Rejected() {
        val orderId = 1L
        val event = OrderEvent.USER_CONFIRM
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
    fun testSendEvent_Exception() {
        val orderId = 1L
        val event = OrderEvent.USER_CONFIRM
        val machineId = orderId.toString()

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } throws RuntimeException("DB error")

        val result = stateMachineService.sendEvent(orderId, event)

        assertEquals(false, result)
    }

    @Test
    @DisplayName("Should return false when sendEvent throws error")
    fun testSendEvent_FluxError() {
        val orderId = 1L
        val event = OrderEvent.USER_CONFIRM
        val machineId = orderId.toString()

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { jpaStateMachineRepository.findById(machineId) } returns Optional.empty()
        every { stateMachine.sendEvent(any<Mono<Message<OrderEvent>>>()) } returns
            Flux.error(RuntimeException("Test error"))
        every { stateMachine.stopReactively() } returns Mono.empty()

        val result = stateMachineService.sendEvent(orderId, event)

        assertEquals(false, result)
    }

    @Test
    @DisplayName("Should handle initialized state machine exception")
    fun testInitializeStateMachine_Exception() {
        val orderId = 1L
        val machineId = orderId.toString()

        every { stateMachineFactory.getStateMachine(machineId) } returns stateMachine
        every { stateMachine.stateMachineAccessor } throws RuntimeException("Accessor error")
        every { stateMachine.stopReactively() } returns Mono.empty()

        stateMachineService.initializeStateMachine(orderId)

        verify { stateMachineFactory.getStateMachine(machineId) }
    }

    private fun mockState(status: OrderStatus): State<OrderStatus, OrderEvent> =
        mockk {
            every { id } returns status
            every { getPseudoState() } returns null
            every { ids } returns listOf(status)
        }
}

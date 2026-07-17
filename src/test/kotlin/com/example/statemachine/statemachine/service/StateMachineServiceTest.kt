package com.example.statemachine.statemachine.service

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
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
    private lateinit var orderJpaRepository: OrderJpaRepository
    private lateinit var stateMachineService: StateMachineService

    @BeforeEach
    fun setUp() {
        stateMachineFactory = mockk()
        jpaStateMachineRepository = mockk()
        stateMachine = mockk(relaxed = true)
        orderJpaRepository = mockk()
        val stateMachineListener =
            mockk<org.springframework.statemachine.listener.StateMachineListenerAdapter<OrderStatus, OrderEvent>>(relaxed = true)
        stateMachineService =
            StateMachineService(
                stateMachineFactory,
                jpaStateMachineRepository,
                stateMachineListener,
                orderJpaRepository,
            )
    }

    @Test
    @DisplayName("Should send event by orderNo successfully")
    fun testSendEventByOrderNoSuccess() {
        val orderNo = "ORD-001"
        val event = OrderEvent.PR_APPROVED

        val eventResult =
            mockk<StateMachineEventResult<OrderStatus, OrderEvent>> {
                every { resultType } returns StateMachineEventResult.ResultType.ACCEPTED
            }

        every { stateMachineFactory.getStateMachine(orderNo) } returns stateMachine
        every { jpaStateMachineRepository.findById(orderNo) } returns Optional.empty()
        every { stateMachine.sendEvent(any<Mono<Message<OrderEvent>>>()) } returns Flux.just(eventResult)
        every { stateMachine.state } returns mockState(OrderStatus.INIT)
        every { stateMachine.stopReactively() } returns Mono.empty()
        every { jpaStateMachineRepository.save(any()) } returns JpaRepositoryStateMachine()

        val result = stateMachineService.sendEventByOrderNo(orderNo, event)

        verify { stateMachineFactory.getStateMachine(orderNo) }
        assertEquals(true, result)
    }

    @Test
    @DisplayName("Should send event with headers successfully")
    fun testSendEventByOrderNoWithHeaders() {
        val orderNo = "ORD-001"
        val event = OrderEvent.PR_APPROVED
        val headers = mapOf<String, Any>("productId" to "PROD-123")

        val eventResult =
            mockk<StateMachineEventResult<OrderStatus, OrderEvent>> {
                every { resultType } returns StateMachineEventResult.ResultType.ACCEPTED
            }

        every { stateMachineFactory.getStateMachine(orderNo) } returns stateMachine
        every { jpaStateMachineRepository.findById(orderNo) } returns Optional.empty()
        every { stateMachine.sendEvent(any<Mono<Message<OrderEvent>>>()) } returns Flux.just(eventResult)
        every { stateMachine.state } returns mockState(OrderStatus.LOCAL_INITIALIZED)
        every { stateMachine.stopReactively() } returns Mono.empty()
        every { jpaStateMachineRepository.save(any()) } returns JpaRepositoryStateMachine()

        val result = stateMachineService.sendEventByOrderNo(orderNo, event, headers)

        verify { stateMachineFactory.getStateMachine(orderNo) }
        assertEquals(true, result)
    }

    @Test
    @DisplayName("Should get current state by orderNo successfully")
    fun testGetCurrentStateByOrderNo() {
        val orderNo = "ORD-001"
        val entity =
            JpaRepositoryStateMachine().apply {
                this.machineId = orderNo
                this.state = OrderStatus.LOCAL_INITIALIZED.name
            }

        every { stateMachineFactory.getStateMachine(orderNo) } returns stateMachine
        every { jpaStateMachineRepository.findById(orderNo) } returns Optional.of(entity)
        every { stateMachine.state } returns mockState(OrderStatus.LOCAL_INITIALIZED)
        every { stateMachine.stopReactively() } returns Mono.empty()

        val result = stateMachineService.getCurrentStateByOrderNo(orderNo)

        assertEquals(OrderStatus.LOCAL_INITIALIZED, result)
    }

    @Test
    @DisplayName("Should return INIT state when no persisted state exists")
    fun testGetCurrentStateByOrderNoNoPersistedState() {
        val orderNo = "ORD-999"

        every { stateMachineFactory.getStateMachine(orderNo) } returns stateMachine
        every { jpaStateMachineRepository.findById(orderNo) } returns Optional.empty()
        every { stateMachine.state } returns mockState(OrderStatus.INIT)
        every { stateMachine.stopReactively() } returns Mono.empty()

        val result = stateMachineService.getCurrentStateByOrderNo(orderNo)

        assertEquals(OrderStatus.INIT, result)
    }

    @Test
    @DisplayName("Should initialize state machine by orderNo")
    fun testInitializeStateMachineByOrderNo() {
        val orderNo = "ORD-001"

        every { stateMachineFactory.getStateMachine(orderNo) } returns stateMachine
        every { stateMachine.stateMachineAccessor } returns mockk(relaxed = true)
        every { jpaStateMachineRepository.save(any()) } returns JpaRepositoryStateMachine()
        every { stateMachine.state } returns mockState(OrderStatus.INIT)
        every { stateMachine.stopReactively() } returns Mono.empty()

        stateMachineService.initializeStateMachineByOrderNo(orderNo)

        verify { stateMachineFactory.getStateMachine(orderNo) }
    }

    @Test
    @DisplayName("Should initialize state machine with custom initial state")
    fun testInitializeStateMachineByOrderNoCustomInitialState() {
        val orderNo = "ORD-001"
        val initialState = OrderStatus.LOCAL_INITIALIZED

        every { stateMachineFactory.getStateMachine(orderNo) } returns stateMachine
        every { stateMachine.stateMachineAccessor } returns mockk(relaxed = true)
        every { jpaStateMachineRepository.save(any()) } returns JpaRepositoryStateMachine()
        every { stateMachine.state } returns mockState(initialState)
        every { stateMachine.stopReactively() } returns Mono.empty()

        stateMachineService.initializeStateMachineByOrderNo(orderNo, initialState)

        verify { stateMachineFactory.getStateMachine(orderNo) }
    }

    @Test
    @DisplayName("Should return false when event send fails with rejected result")
    fun testSendEventByOrderNoRejected() {
        val orderNo = "ORD-001"
        val event = OrderEvent.VOM

        val eventResult =
            mockk<StateMachineEventResult<OrderStatus, OrderEvent>> {
                every { resultType } returns StateMachineEventResult.ResultType.DENIED
            }

        every { stateMachineFactory.getStateMachine(orderNo) } returns stateMachine
        every { jpaStateMachineRepository.findById(orderNo) } returns Optional.empty()
        every { stateMachine.sendEvent(any<Mono<Message<OrderEvent>>>()) } returns Flux.just(eventResult)
        every { stateMachine.stopReactively() } returns Mono.empty()

        val result = stateMachineService.sendEventByOrderNo(orderNo, event)

        assertEquals(false, result)
    }

    @Test
    @DisplayName("Should return false on exception")
    fun testSendEventByOrderNoException() {
        val orderNo = "ORD-001"
        val event = OrderEvent.DOM

        every { stateMachineFactory.getStateMachine(orderNo) } returns stateMachine
        every { jpaStateMachineRepository.findById(orderNo) } throws RuntimeException("DB error")

        val result = stateMachineService.sendEventByOrderNo(orderNo, event)

        assertEquals(false, result)
    }

    private fun mockState(status: OrderStatus): State<OrderStatus, OrderEvent> =
        mockk {
            every { id } returns status
            every { getPseudoState() } returns null
            every { ids } returns listOf(status)
        }
}

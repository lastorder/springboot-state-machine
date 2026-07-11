package com.example.statemachine.statemachine

import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineBuilder
import java.math.BigDecimal

class OrderStateMachineTest {

    @Test
    @DisplayName("Should transition from CREATED to PENDING_PAYMENT on SUBMIT event")
    fun testSubmitTransition() {
        val stateMachine = buildStateMachine()
        stateMachine.start()

        assertEquals(OrderStatus.CREATED, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.SUBMIT)
        assertEquals(OrderStatus.PENDING_PAYMENT, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from PENDING_PAYMENT to PAID on PAY event")
    fun testPayTransition() {
        val stateMachine = buildStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT)
        assertEquals(OrderStatus.PENDING_PAYMENT, stateMachine.state.id)

        val message = MessageBuilder
            .withPayload(OrderEvent.PAY)
            .setHeader("orderId", 1L)
            .setHeader("amount", BigDecimal("100.00"))
            .build()

        stateMachine.sendEvent(message)
        assertEquals(OrderStatus.PAID, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from PAID to PENDING_SHIPMENT on CONFIRM_PAYMENT event")
    fun testConfirmPaymentTransition() {
        val stateMachine = buildStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT)
        stateMachine.sendEvent(
            MessageBuilder
                .withPayload(OrderEvent.PAY)
                .setHeader("orderId", 1L)
                .setHeader("amount", BigDecimal("100.00"))
                .build(),
        )
        assertEquals(OrderStatus.PAID, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.CONFIRM_PAYMENT)
        assertEquals(OrderStatus.PENDING_SHIPMENT, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from PENDING_SHIPMENT to SHIPPED on SHIP event")
    fun testShipTransition() {
        val stateMachine = buildStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT)
        stateMachine.sendEvent(
            MessageBuilder
                .withPayload(OrderEvent.PAY)
                .setHeader("orderId", 1L)
                .setHeader("amount", BigDecimal("100.00"))
                .build(),
        )
        stateMachine.sendEvent(OrderEvent.CONFIRM_PAYMENT)
        assertEquals(OrderStatus.PENDING_SHIPMENT, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.SHIP)
        assertEquals(OrderStatus.SHIPPED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from SHIPPED to DELIVERED on DELIVER event")
    fun testDeliverTransition() {
        val stateMachine = buildStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT)
        stateMachine.sendEvent(
            MessageBuilder
                .withPayload(OrderEvent.PAY)
                .setHeader("orderId", 1L)
                .setHeader("amount", BigDecimal("100.00"))
                .build(),
        )
        stateMachine.sendEvent(OrderEvent.CONFIRM_PAYMENT)
        stateMachine.sendEvent(OrderEvent.SHIP)
        assertEquals(OrderStatus.SHIPPED, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.DELIVER)
        assertEquals(OrderStatus.DELIVERED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from CREATED to CANCELLED on CANCEL event")
    fun testCancelFromCreated() {
        val stateMachine = buildStateMachine()
        stateMachine.start()

        assertEquals(OrderStatus.CREATED, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.CANCEL)
        assertEquals(OrderStatus.CANCELLED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from PENDING_PAYMENT to CANCELLED on CANCEL event")
    fun testCancelFromPendingPayment() {
        val stateMachine = buildStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT)
        assertEquals(OrderStatus.PENDING_PAYMENT, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.CANCEL)
        assertEquals(OrderStatus.CANCELLED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should not transition from SHIPPED on CANCEL event")
    fun testCannotCancelFromShipped() {
        val stateMachine = buildStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT)
        stateMachine.sendEvent(
            MessageBuilder
                .withPayload(OrderEvent.PAY)
                .setHeader("orderId", 1L)
                .setHeader("amount", BigDecimal("100.00"))
                .build(),
        )
        stateMachine.sendEvent(OrderEvent.CONFIRM_PAYMENT)
        stateMachine.sendEvent(OrderEvent.SHIP)
        assertEquals(OrderStatus.SHIPPED, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.CANCEL)
        assertEquals(OrderStatus.SHIPPED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from PAID to REFUNDED on REFUND event")
    fun testRefundFromPaid() {
        val stateMachine = buildStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT)
        stateMachine.sendEvent(
            MessageBuilder
                .withPayload(OrderEvent.PAY)
                .setHeader("orderId", 1L)
                .setHeader("amount", BigDecimal("100.00"))
                .build(),
        )
        assertEquals(OrderStatus.PAID, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.REFUND)
        assertEquals(OrderStatus.REFUNDED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from DELIVERED to REFUNDED on REFUND event - DELIVERED is end state so no transition")
    fun testRefundFromDelivered() {
        val stateMachine = buildStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT)
        stateMachine.sendEvent(
            MessageBuilder
                .withPayload(OrderEvent.PAY)
                .setHeader("orderId", 1L)
                .setHeader("amount", BigDecimal("100.00"))
                .build(),
        )
        stateMachine.sendEvent(OrderEvent.CONFIRM_PAYMENT)
        stateMachine.sendEvent(OrderEvent.SHIP)
        stateMachine.sendEvent(OrderEvent.DELIVER)
        assertEquals(OrderStatus.DELIVERED, stateMachine.state.id)

        // DELIVERED is an end state, cannot transition to REFUNDED
        stateMachine.sendEvent(OrderEvent.REFUND)
        assertEquals(OrderStatus.DELIVERED, stateMachine.state.id)

        stateMachine.stop()
    }

    private fun buildStateMachine(): StateMachine<OrderStatus, OrderEvent> {
        val builder = StateMachineBuilder.builder<OrderStatus, OrderEvent>()

        builder.configureStates()
            .withStates()
            .initial(OrderStatus.CREATED)
            .state(OrderStatus.PENDING_PAYMENT)
            .state(OrderStatus.PAID)
            .state(OrderStatus.PENDING_SHIPMENT)
            .state(OrderStatus.SHIPPED)
            .end(OrderStatus.DELIVERED)
            .end(OrderStatus.CANCELLED)
            .end(OrderStatus.REFUNDED)

        builder.configureTransitions()
            .withExternal()
            .source(OrderStatus.CREATED).target(OrderStatus.PENDING_PAYMENT)
            .event(OrderEvent.SUBMIT)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.PAID)
            .event(OrderEvent.PAY)
            .and()
            .withExternal()
            .source(OrderStatus.PAID).target(OrderStatus.PENDING_SHIPMENT)
            .event(OrderEvent.CONFIRM_PAYMENT)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_SHIPMENT).target(OrderStatus.SHIPPED)
            .event(OrderEvent.SHIP)
            .and()
            .withExternal()
            .source(OrderStatus.SHIPPED).target(OrderStatus.DELIVERED)
            .event(OrderEvent.DELIVER)
            .and()
            .withExternal()
            .source(OrderStatus.CREATED).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_SHIPMENT).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .and()
            .withExternal()
            .source(OrderStatus.PAID).target(OrderStatus.REFUNDED)
            .event(OrderEvent.REFUND)
            .and()
            .withExternal()
            .source(OrderStatus.DELIVERED).target(OrderStatus.REFUNDED)
            .event(OrderEvent.REFUND)

        return builder.build()
    }
}

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
    @DisplayName("Should transition from CREATED to PENDING_VALIDATION on SUBMIT_VALIDATION")
    fun testSubmitValidationTransition() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        assertEquals(OrderStatus.CREATED, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        assertEquals(OrderStatus.PENDING_VALIDATION, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from PENDING_VALIDATION to PENDING_CONFIRMATION when both succeed")
    fun testBothValidationSucceed() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        assertEquals(OrderStatus.PENDING_CONFIRMATION, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition to CANCELLED on INVENTORY_FAILED")
    fun testInventoryFailedTransition() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_FAILED)
        assertEquals(OrderStatus.CANCELLED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition to CANCELLED on PRICING_FAILED")
    fun testPricingFailedTransition() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.PRICING_FAILED)
        assertEquals(OrderStatus.CANCELLED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition to CANCELLED on VALIDATION_TIMEOUT")
    fun testValidationTimeoutTransition() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.VALIDATION_TIMEOUT)
        assertEquals(OrderStatus.CANCELLED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should retry validation back to CREATED")
    fun testRetryValidationTransition() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.RETRY_VALIDATION)
        assertEquals(OrderStatus.CREATED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from PENDING_CONFIRMATION to PENDING_PAYMENT on USER_CONFIRM")
    fun testUserConfirmTransition() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        assertEquals(OrderStatus.PENDING_CONFIRMATION, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.USER_CONFIRM)
        assertEquals(OrderStatus.PENDING_PAYMENT, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from PENDING_CONFIRMATION to REJECTED on USER_REJECT")
    fun testUserRejectTransition() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        stateMachine.sendEvent(OrderEvent.USER_REJECT)
        assertEquals(OrderStatus.REJECTED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from PENDING_CONFIRMATION to CREATED on MODIFY_ORDER")
    fun testModifyOrderFromConfirmationTransition() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        stateMachine.sendEvent(OrderEvent.MODIFY_ORDER)
        assertEquals(OrderStatus.CREATED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should transition from PENDING_PAYMENT to CREATED on MODIFY_ORDER")
    fun testModifyOrderFromPaymentTransition() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        stateMachine.sendEvent(OrderEvent.USER_CONFIRM)
        stateMachine.sendEvent(OrderEvent.MODIFY_ORDER)
        assertEquals(OrderStatus.CREATED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should complete full order flow")
    fun testFullOrderFlow() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        assertEquals(OrderStatus.PENDING_CONFIRMATION, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.USER_CONFIRM)
        assertEquals(OrderStatus.PENDING_PAYMENT, stateMachine.state.id)

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

        stateMachine.sendEvent(OrderEvent.SHIP)
        assertEquals(OrderStatus.SHIPPED, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.DELIVER)
        assertEquals(OrderStatus.DELIVERED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should handle order modification cycle")
    fun testOrderModificationCycle() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        stateMachine.sendEvent(OrderEvent.MODIFY_ORDER)
        assertEquals(OrderStatus.CREATED, stateMachine.state.id)

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        stateMachine.sendEvent(OrderEvent.USER_CONFIRM)
        assertEquals(OrderStatus.PENDING_PAYMENT, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should cancel order from PENDING_VALIDATION")
    fun testCancelFromPendingValidation() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.CANCEL)
        assertEquals(OrderStatus.CANCELLED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should cancel order from PENDING_CONFIRMATION")
    fun testCancelFromPendingConfirmation() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        stateMachine.sendEvent(OrderEvent.CANCEL)
        assertEquals(OrderStatus.CANCELLED, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should handle INVENTORY_MODIFIED from PENDING_CONFIRMATION")
    fun testInventoryModifiedFromPendingConfirmation() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        stateMachine.sendEvent(OrderEvent.INVENTORY_MODIFIED)
        assertEquals(OrderStatus.PENDING_CONFIRMATION, stateMachine.state.id)

        stateMachine.stop()
    }

    @Test
    @DisplayName("Should handle INVENTORY_MODIFIED from PENDING_PAYMENT")
    fun testInventoryModifiedFromPendingPayment() {
        val stateMachine = buildSimpleStateMachine()
        stateMachine.start()

        stateMachine.sendEvent(OrderEvent.SUBMIT_VALIDATION)
        stateMachine.sendEvent(OrderEvent.INVENTORY_SUCCESS)
        stateMachine.sendEvent(OrderEvent.PRICING_SUCCESS)
        stateMachine.sendEvent(OrderEvent.USER_CONFIRM)
        stateMachine.sendEvent(OrderEvent.INVENTORY_MODIFIED)
        assertEquals(OrderStatus.PENDING_CONFIRMATION, stateMachine.state.id)

        stateMachine.stop()
    }

    private fun buildSimpleStateMachine(): StateMachine<OrderStatus, OrderEvent> {
        val builder = StateMachineBuilder.builder<OrderStatus, OrderEvent>()

        builder.configureStates()
            .withStates()
            .initial(OrderStatus.CREATED)
            .state(OrderStatus.PENDING_VALIDATION)
            .state(OrderStatus.PENDING_CONFIRMATION)
            .state(OrderStatus.PENDING_PAYMENT)
            .state(OrderStatus.PAID)
            .state(OrderStatus.PENDING_SHIPMENT)
            .state(OrderStatus.SHIPPED)
            .end(OrderStatus.DELIVERED)
            .end(OrderStatus.CANCELLED)
            .end(OrderStatus.REJECTED)
            .end(OrderStatus.REFUNDED)

        builder.configureTransitions()
            .withExternal()
            .source(OrderStatus.CREATED).target(OrderStatus.PENDING_VALIDATION)
            .event(OrderEvent.SUBMIT_VALIDATION)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.PENDING_CONFIRMATION)
            .event(OrderEvent.PRICING_SUCCESS)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.CANCELLED)
            .event(OrderEvent.INVENTORY_FAILED)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.CANCELLED)
            .event(OrderEvent.PRICING_FAILED)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.CANCELLED)
            .event(OrderEvent.VALIDATION_TIMEOUT)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_VALIDATION).target(OrderStatus.CREATED)
            .event(OrderEvent.RETRY_VALIDATION)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_CONFIRMATION).target(OrderStatus.PENDING_PAYMENT)
            .event(OrderEvent.USER_CONFIRM)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_CONFIRMATION).target(OrderStatus.REJECTED)
            .event(OrderEvent.USER_REJECT)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_CONFIRMATION).target(OrderStatus.CREATED)
            .event(OrderEvent.MODIFY_ORDER)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_CONFIRMATION).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_CONFIRMATION).target(OrderStatus.PENDING_CONFIRMATION)
            .event(OrderEvent.INVENTORY_MODIFIED)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.CREATED)
            .event(OrderEvent.MODIFY_ORDER)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.PAID)
            .event(OrderEvent.PAY)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.PENDING_CONFIRMATION)
            .event(OrderEvent.INVENTORY_MODIFIED)
            .and()
            .withExternal()
            .source(OrderStatus.PAID).target(OrderStatus.PENDING_SHIPMENT)
            .event(OrderEvent.CONFIRM_PAYMENT)
            .and()
            .withExternal()
            .source(OrderStatus.PAID).target(OrderStatus.REFUNDED)
            .event(OrderEvent.REFUND)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_SHIPMENT).target(OrderStatus.SHIPPED)
            .event(OrderEvent.SHIP)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_SHIPMENT).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .and()
            .withExternal()
            .source(OrderStatus.SHIPPED).target(OrderStatus.DELIVERED)
            .event(OrderEvent.DELIVER)
            .and()
            .withExternal()
            .source(OrderStatus.DELIVERED).target(OrderStatus.REFUNDED)
            .event(OrderEvent.REFUND)

        return builder.build()
    }
}

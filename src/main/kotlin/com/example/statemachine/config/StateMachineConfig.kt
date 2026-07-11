package com.example.statemachine.config

import com.example.statemachine.action.NotifyAction
import com.example.statemachine.action.PaymentAction
import com.example.statemachine.action.ShipAction
import com.example.statemachine.action.SubmitAction
import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.guard.PaymentGuard
import org.springframework.context.annotation.Configuration
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.StateMachineConfigurerAdapter
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer

@Configuration
@EnableStateMachineFactory
class StateMachineConfig(
    private val submitAction: SubmitAction,
    private val paymentAction: PaymentAction,
    private val shipAction: ShipAction,
    private val notifyAction: NotifyAction,
    private val paymentGuard: PaymentGuard,
) : StateMachineConfigurerAdapter<OrderStatus, OrderEvent>() {
    override fun configure(states: StateMachineStateConfigurer<OrderStatus, OrderEvent>) {
        states.withStates()
            .initial(OrderStatus.CREATED)
            .state(OrderStatus.PENDING_PAYMENT)
            .state(OrderStatus.PAID)
            .state(OrderStatus.PENDING_SHIPMENT)
            .state(OrderStatus.SHIPPED)
            .end(OrderStatus.DELIVERED)
            .end(OrderStatus.CANCELLED)
            .end(OrderStatus.REFUNDED)
    }

    override fun configure(transitions: StateMachineTransitionConfigurer<OrderStatus, OrderEvent>) {
        transitions
            .withExternal()
            .source(OrderStatus.CREATED).target(OrderStatus.PENDING_PAYMENT)
            .event(OrderEvent.SUBMIT)
            .action(submitAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.PAID)
            .event(OrderEvent.PAY)
            .guard(paymentGuard)
            .action(paymentAction)
            .and()
            .withExternal()
            .source(OrderStatus.PAID).target(OrderStatus.PENDING_SHIPMENT)
            .event(OrderEvent.CONFIRM_PAYMENT)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_SHIPMENT).target(OrderStatus.SHIPPED)
            .event(OrderEvent.SHIP)
            .action(shipAction)
            .and()
            .withExternal()
            .source(OrderStatus.SHIPPED).target(OrderStatus.DELIVERED)
            .event(OrderEvent.DELIVER)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.CREATED).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_PAYMENT).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.PENDING_SHIPMENT).target(OrderStatus.CANCELLED)
            .event(OrderEvent.CANCEL)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.PAID).target(OrderStatus.REFUNDED)
            .event(OrderEvent.REFUND)
            .action(notifyAction)
            .and()
            .withExternal()
            .source(OrderStatus.DELIVERED).target(OrderStatus.REFUNDED)
            .event(OrderEvent.REFUND)
            .action(notifyAction)
    }
}

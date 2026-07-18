package com.example.statemachine.statemachine.config

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.statemachine.action.PrApprovedAction
import com.example.statemachine.statemachine.action.SendCoeAction
import com.example.statemachine.statemachine.action.SyncDealAction
import org.springframework.context.annotation.Configuration
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.StateMachineConfigurerAdapter
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer

@Configuration
@EnableStateMachineFactory
class StateMachineConfig(
    private val prApprovedAction: PrApprovedAction,
    private val sendCoeAction: SendCoeAction,
    private val syncDealAction: SyncDealAction,
) : StateMachineConfigurerAdapter<OrderStatus, OrderEvent>() {
    override fun configure(states: StateMachineStateConfigurer<OrderStatus, OrderEvent>) {
        states
            .withStates()
            .initial(OrderStatus.INIT)
            .state(OrderStatus.LOCAL_INITIALIZED)
            .state(OrderStatus.FACTORY_ORDER_SUBMITTED, syncDealAction, null)
            .state(OrderStatus.ORDER_INITIALIZE_SUCCEED)
            .end(OrderStatus.ORDER_INITIALIZE_FAILED)
    }

    override fun configure(transitions: StateMachineTransitionConfigurer<OrderStatus, OrderEvent>) {
        transitions
            .withExternal()
            .source(OrderStatus.INIT)
            .target(OrderStatus.LOCAL_INITIALIZED)
            .event(OrderEvent.PR_APPROVED)
            .action(prApprovedAction)
            .and()
            .withExternal()
            .source(OrderStatus.LOCAL_INITIALIZED)
            .target(OrderStatus.FACTORY_ORDER_SUBMITTED)
            .action(sendCoeAction)
            .and()
            .withExternal()
            .source(OrderStatus.FACTORY_ORDER_SUBMITTED)
            .target(OrderStatus.ORDER_INITIALIZE_SUCCEED)
            .event(OrderEvent.VOM)
            .and()
            .withExternal()
            .source(OrderStatus.FACTORY_ORDER_SUBMITTED)
            .target(OrderStatus.ORDER_INITIALIZE_SUCCEED)
            .event(OrderEvent.DOM)
            .and()
            .withExternal()
            .source(OrderStatus.FACTORY_ORDER_SUBMITTED)
            .target(OrderStatus.ORDER_INITIALIZE_FAILED)
            .event(OrderEvent.VOM_FAILED)
    }
}

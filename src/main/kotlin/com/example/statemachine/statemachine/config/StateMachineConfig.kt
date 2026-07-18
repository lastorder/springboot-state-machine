package com.example.statemachine.statemachine.config

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.statemachine.action.BroadcastCdoaAcceptAction
import com.example.statemachine.statemachine.action.BroadcastPurchaseRequestAcceptAction
import com.example.statemachine.statemachine.action.BroadcastPurchaseRequestAcceptRetryAction
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
    private val broadcastPurchaseRequestAcceptAction: BroadcastPurchaseRequestAcceptAction,
    private val broadcastPurchaseRequestAcceptRetryAction: BroadcastPurchaseRequestAcceptRetryAction,
    private val broadcastCdoaAcceptAction: BroadcastCdoaAcceptAction,
) : StateMachineConfigurerAdapter<OrderStatus, OrderEvent>() {
    override fun configure(states: StateMachineStateConfigurer<OrderStatus, OrderEvent>) {
        states
            .withStates()
            .initial(OrderStatus.INIT)
            .state(OrderStatus.LOCAL_INITIALIZED)
            .state(OrderStatus.FACTORY_ORDER_SUBMITTED, syncDealAction, null)
            .state(OrderStatus.ORDER_INITIALIZE_SUCCEED)
            .end(OrderStatus.ORDER_INITIALIZE_FAILED)
            .state(OrderStatus.PURCHASE_REQUEST_ACCEPTING, broadcastPurchaseRequestAcceptAction, null)
            .state(OrderStatus.PURCHASE_REQUEST_ACCEPTED)
            .state(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED)
            .state(OrderStatus.CDOA_ACCEPTING, broadcastCdoaAcceptAction, null)
            .state(OrderStatus.CDOA_ACCEPTED)
            .end(OrderStatus.CDOA_ACCEPT_FAILED)
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
            .and()
            .withExternal()
            .source(OrderStatus.ORDER_INITIALIZE_SUCCEED)
            .target(OrderStatus.PURCHASE_REQUEST_ACCEPTING)
            .event(OrderEvent.PURCHASE_REQUEST_ACCEPT)
            .and()
            .withExternal()
            .source(OrderStatus.PURCHASE_REQUEST_ACCEPTING)
            .target(OrderStatus.PURCHASE_REQUEST_ACCEPTED)
            .event(OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS)
            .and()
            .withExternal()
            .source(OrderStatus.PURCHASE_REQUEST_ACCEPTING)
            .target(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED)
            .event(OrderEvent.PURCHASE_REQUEST_ACCEPT_FAILED)
            .and()
            .withExternal()
            .source(OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED)
            .target(OrderStatus.PURCHASE_REQUEST_ACCEPTING)
            .event(OrderEvent.PURCHASE_REQUEST_ACCEPT_RETRY)
            .action(broadcastPurchaseRequestAcceptRetryAction)
            .and()
            .withExternal()
            .source(OrderStatus.PURCHASE_REQUEST_ACCEPTED)
            .target(OrderStatus.CDOA_ACCEPTING)
            .event(OrderEvent.CDOA_ACCEPT)
            .and()
            .withExternal()
            .source(OrderStatus.CDOA_ACCEPTING)
            .target(OrderStatus.CDOA_ACCEPTED)
            .event(OrderEvent.CDOA_ACCEPT_SUCCESS)
            .and()
            .withExternal()
            .source(OrderStatus.CDOA_ACCEPTING)
            .target(OrderStatus.CDOA_ACCEPT_FAILED)
            .event(OrderEvent.CDOA_ACCEPT_FAILED)
    }
}

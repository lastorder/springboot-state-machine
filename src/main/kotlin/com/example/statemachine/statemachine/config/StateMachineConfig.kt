package com.example.statemachine.statemachine.config

import com.example.statemachine.core.StateMachineFactory
import com.example.statemachine.core.Transition
import com.example.statemachine.core.TransitionTable
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.persistence.OrderStateMachineRepository
import com.example.statemachine.infrastructure.persistence.StateMachineJpaRepository
import com.example.statemachine.statemachine.action.BroadcastCdoaAcceptAction
import com.example.statemachine.statemachine.action.BroadcastPurchaseRequestAcceptAction
import com.example.statemachine.statemachine.action.BroadcastPurchaseRequestAcceptRetryAction
import com.example.statemachine.statemachine.action.PrApprovedAction
import com.example.statemachine.statemachine.action.SendCoeAction
import com.example.statemachine.statemachine.action.SyncDealAction
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class StateMachineConfig {
    @Bean
    fun transitionTable(
        prApprovedAction: PrApprovedAction,
        sendCoeAction: SendCoeAction,
        syncDealAction: SyncDealAction,
        broadcastPurchaseRequestAcceptAction: BroadcastPurchaseRequestAcceptAction,
        broadcastPurchaseRequestAcceptRetryAction: BroadcastPurchaseRequestAcceptRetryAction,
        broadcastCdoaAcceptAction: BroadcastCdoaAcceptAction,
    ): TransitionTable<OrderStatus> =
        TransitionTable<OrderStatus>().apply {
            add(
                Transition(
                    source = OrderStatus.INIT,
                    target = OrderStatus.LOCAL_INITIALIZED,
                    event = OrderEvent.PR_APPROVED,
                    action = prApprovedAction,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.LOCAL_INITIALIZED,
                    target = OrderStatus.FACTORY_ORDER_SUBMITTED,
                    event = null,
                    action = sendCoeAction,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.FACTORY_ORDER_SUBMITTED,
                    target = OrderStatus.ORDER_INITIALIZE_SUCCEED,
                    event = OrderEvent.VOM,
                    action = syncDealAction,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.FACTORY_ORDER_SUBMITTED,
                    target = OrderStatus.ORDER_INITIALIZE_SUCCEED,
                    event = OrderEvent.DOM,
                    action = syncDealAction,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.FACTORY_ORDER_SUBMITTED,
                    target = OrderStatus.ORDER_INITIALIZE_FAILED,
                    event = OrderEvent.VOM_FAILED,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.ORDER_INITIALIZE_SUCCEED,
                    target = OrderStatus.PURCHASE_REQUEST_ACCEPTING,
                    event = OrderEvent.PURCHASE_REQUEST_ACCEPT,
                    action = broadcastPurchaseRequestAcceptAction,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.PURCHASE_REQUEST_ACCEPTING,
                    target = OrderStatus.PURCHASE_REQUEST_ACCEPTED,
                    event = OrderEvent.PURCHASE_REQUEST_ACCEPT_SUCCESS,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.PURCHASE_REQUEST_ACCEPTING,
                    target = OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED,
                    event = OrderEvent.PURCHASE_REQUEST_ACCEPT_FAILED,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.PURCHASE_REQUEST_ACCEPT_FAILED,
                    target = OrderStatus.PURCHASE_REQUEST_ACCEPTING,
                    event = OrderEvent.PURCHASE_REQUEST_ACCEPT_RETRY,
                    action = broadcastPurchaseRequestAcceptRetryAction,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.PURCHASE_REQUEST_ACCEPTED,
                    target = OrderStatus.CDOA_ACCEPTING,
                    event = OrderEvent.CDOA_ACCEPT,
                    action = broadcastCdoaAcceptAction,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.CDOA_ACCEPTING,
                    target = OrderStatus.CDOA_ACCEPTED,
                    event = OrderEvent.CDOA_ACCEPT_SUCCESS,
                ),
            )
            add(
                Transition(
                    source = OrderStatus.CDOA_ACCEPTING,
                    target = OrderStatus.CDOA_ACCEPT_FAILED,
                    event = OrderEvent.CDOA_ACCEPT_FAILED,
                ),
            )
        }

    @Bean
    fun stateMachineRepository(
        jpaRepository: StateMachineJpaRepository,
        transitionTable: TransitionTable<OrderStatus>,
        stateMachineListener: StateMachineListener,
    ): OrderStateMachineRepository =
        OrderStateMachineRepository(
            jpaRepository = jpaRepository,
            transitionTable = transitionTable,
            listener = stateMachineListener,
        )

    @Bean
    fun stateMachineFactory(
        transitionTable: TransitionTable<OrderStatus>,
        repository: OrderStateMachineRepository,
        stateMachineListener: StateMachineListener,
    ): StateMachineFactory<OrderStatus> =
        StateMachineFactory(
            initialState = OrderStatus.INIT,
            transitionTable = transitionTable,
            listener = stateMachineListener,
            repository = repository,
        )
}

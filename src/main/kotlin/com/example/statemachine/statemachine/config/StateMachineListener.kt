package com.example.statemachine.statemachine.config

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State
import org.springframework.statemachine.transition.Transition
import org.springframework.stereotype.Component

@Component
class StateMachineListener : StateMachineListenerAdapter<OrderStatus, OrderEvent>() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun stateChanged(
        from: State<OrderStatus, OrderEvent>?,
        to: State<OrderStatus, OrderEvent>?,
    ) {
        log.info("State changed: ${from?.id} -> ${to?.id}")
    }

    override fun transition(transition: Transition<OrderStatus, OrderEvent>) {
        log.info(
            "Transition: ${transition.source?.id} -> ${transition.target?.id}, trigger=${transition.trigger}",
        )
    }

    override fun eventNotAccepted(event: Message<OrderEvent>) {
        log.warn("Event not accepted: ${event.payload}")
    }

    override fun stateMachineError(
        stateMachine: StateMachine<OrderStatus, OrderEvent>,
        exception: Exception,
    ) {
        log.error("State machine error", exception)
    }

    override fun extendedStateChanged(
        key: Any?,
        value: Any?,
    ) {
        log.debug("Extended state changed: $key = $value")
    }

    override fun stateEntered(state: State<OrderStatus, OrderEvent>) {
        log.debug("State entered: ${state.id}")
    }

    override fun stateExited(state: State<OrderStatus, OrderEvent>) {
        log.debug("State exited: ${state.id}")
    }
}

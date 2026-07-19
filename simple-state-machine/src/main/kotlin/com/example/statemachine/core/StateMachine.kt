package com.example.statemachine.core

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.Event
import com.example.statemachine.api.State
import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.api.StateContext
import com.example.statemachine.persistence.StateMachineRepository
import org.slf4j.LoggerFactory

class StateMachine<S : State, E : Event> private constructor(
    private val initialState: S,
    private val transitionTable: TransitionTable<S, E>,
    private val listener: StateChangedListener<S, E>?,
    private val repository: StateMachineRepository<S>?,
) {
    fun sendEvent(
        machineId: String,
        event: E,
        headers: Map<String, Any?> = emptyMap(),
    ): Boolean {
        log.debug("Sending event: machineId={}, event={}", machineId, event)

        val currentState = repository?.findById(machineId) ?: initialState
        log.debug("Current state: {}", currentState)

        val transition = transitionTable.findByEvent(currentState, event)
        if (transition == null) {
            log.warn("No transition found for state={}, event={}", currentState, event)
            return false
        }

        return executeTransition(machineId, currentState, transition, event, headers)
    }

    fun getCurrentState(machineId: String): S {
        return repository?.findById(machineId) ?: initialState
    }

    fun reset(machineId: String) {
        repository?.delete(machineId)
        log.debug("Reset state machine: machineId={}", machineId)
    }

    private fun executeTransition(
        machineId: String,
        currentState: S,
        transition: Transition<S, E>,
        event: E,
        headers: Map<String, Any?>,
    ): Boolean {
        val context =
            StateContext(
                machineId = machineId,
                sourceState = currentState,
                targetState = transition.target,
                event = event,
                headers = headers,
            )

        val actionResult = transition.action?.execute(context) ?: ActionResult.success()

        return when (actionResult) {
            is ActionResult.Failure -> {
                log.warn(
                    "Action failed: machineId={}, reason={}",
                    machineId,
                    actionResult.reason,
                )
                false
            }

            is ActionResult.Success -> {
                val newState = transition.target
                repository?.save(machineId, newState)
                log.info("State changed: machineId={}, {} -> {}", machineId, currentState, newState)

                listener?.onStateChanged(
                    StateContext(
                        machineId = machineId,
                        sourceState = currentState,
                        targetState = newState,
                        event = event,
                        headers = headers,
                        extendedState = context.extendedState,
                    ),
                )

                executeAutoTransitionIfNeeded(machineId, newState, headers, context.extendedState)

                true
            }
        }
    }

    private fun executeAutoTransitionIfNeeded(
        machineId: String,
        currentState: S,
        headers: Map<String, Any?>,
        extendedState: MutableMap<String, Any?> = mutableMapOf(),
    ) {
        val autoTransition = transitionTable.findAutoTransition(currentState)
        if (autoTransition != null) {
            log.debug("Executing auto transition: {} -> {}", currentState, autoTransition.target)

            val context: StateContext<S, E> =
                StateContext(
                    machineId = machineId,
                    sourceState = currentState,
                    targetState = autoTransition.target,
                    event = null,
                    headers = headers,
                    extendedState = extendedState,
                )

            val actionResult = autoTransition.action?.execute(context) ?: ActionResult.success()

            when (actionResult) {
                is ActionResult.Failure -> {
                    log.warn(
                        "Auto transition action failed: machineId={}, reason={}",
                        machineId,
                        actionResult.reason,
                    )
                }

                is ActionResult.Success -> {
                    val newState = autoTransition.target
                    repository?.save(machineId, newState)
                    log.info("Auto transition: machineId={}, {} -> {}", machineId, currentState, newState)

                    listener?.onStateChanged(
                        StateContext(
                            machineId = machineId,
                            sourceState = currentState,
                            targetState = newState,
                            event = null,
                            headers = headers,
                            extendedState = extendedState,
                        ),
                    )

                    executeAutoTransitionIfNeeded(machineId, newState, headers, extendedState)
                }
            }
        }
    }

    class Builder<S : State, E : Event> {
        var initialState: S? = null
        private val transitionTable = TransitionTable<S, E>()
        var listener: StateChangedListener<S, E>? = null
        var repository: StateMachineRepository<S>? = null

        fun transition(block: TransitionBuilder<S, E>.() -> Unit) {
            val builder = TransitionBuilder<S, E>()
            builder.block()
            transitionTable.add(builder.build())
        }

        fun build(): StateMachine<S, E> {
            checkNotNull(initialState) { "Initial state must be set" }
            return StateMachine(initialState!!, transitionTable, listener, repository)
        }
    }

    class TransitionBuilder<S : State, E : Event> {
        private var source: S? = null
        private var target: S? = null
        private var event: E? = null
        private var action: Action<S, E>? = null

        fun from(state: S) = apply { source = state }

        fun to(state: S) = apply { target = state }

        fun on(e: E) = apply { event = e }

        fun action(a: Action<S, E>) = apply { action = a }

        fun action(block: (StateContext<S, E>) -> ActionResult<E>) =
            apply {
                action =
                    object : Action<S, E> {
                        override fun execute(context: StateContext<S, E>): ActionResult<E> = block(context)
                    }
            }

        fun build(): Transition<S, E> {
            checkNotNull(source) { "Source state must be set" }
            checkNotNull(target) { "Target state must be set" }
            return Transition(source!!, target!!, event, action)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(StateMachine::class.java)
    }
}

fun <S : State, E : Event> stateMachine(block: StateMachine.Builder<S, E>.() -> Unit): StateMachine<S, E> {
    val builder = StateMachine.Builder<S, E>()
    builder.block()
    return builder.build()
}

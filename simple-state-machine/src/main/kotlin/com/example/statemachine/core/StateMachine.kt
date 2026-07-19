package com.example.statemachine.core

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.api.StateContext
import com.example.statemachine.persistence.StateMachineRepository
import org.slf4j.LoggerFactory

class StateMachine<S : Enum<S>> internal constructor(
    val id: String,
    private var currentState: S,
    val initialState: S,
    private val extendedState: MutableMap<String, Any?>,
    private val transitionTable: TransitionTable<S>,
    private val listener: StateChangedListener<S>?,
    private val repository: StateMachineRepository<S>?,
) {
    val state: S get() = currentState

    fun sendEvent(
        event: Enum<*>,
        headers: Map<String, Any?> = emptyMap(),
    ): Boolean {
        log.debug("Sending event: machineId={}, event={}", id, event)
        log.debug("Current state: {}", currentState)

        val transition = transitionTable.findByEvent(currentState, event)
        if (transition == null) {
            log.warn("No transition found for state={}, event={}", currentState, event)
            return false
        }

        return executeTransition(currentState, transition, event, headers)
    }

    fun reset() {
        repository?.deleteById(id)
        log.debug("Reset state machine: machineId={}", id)
    }

    private fun executeTransition(
        sourceState: S,
        transition: Transition<S>,
        event: Enum<*>,
        headers: Map<String, Any?>,
    ): Boolean {
        val context =
            StateContext(
                machineId = id,
                sourceState = sourceState,
                targetState = transition.target,
                event = event,
                headers = headers,
                extendedState = extendedState,
            )

        val actionResult = transition.action?.execute(context) ?: ActionResult.success()

        return when (actionResult) {
            is ActionResult.Failure -> {
                log.warn(
                    "Action failed: machineId={}, reason={}",
                    id,
                    actionResult.reason,
                )
                false
            }

            is ActionResult.Success -> {
                val newState = transition.target
                currentState = newState
                repository?.save(this)
                log.info("State changed: machineId={}, {} -> {}", id, sourceState, newState)

                listener?.onStateChanged(
                    StateContext(
                        machineId = id,
                        sourceState = sourceState,
                        targetState = newState,
                        event = event,
                        headers = headers,
                        extendedState = extendedState,
                    ),
                )

                executeAutoTransitionIfNeeded(newState, headers)

                actionResult.nextEvent?.let { sendEvent(it, headers) }

                true
            }
        }
    }

    private fun executeAutoTransitionIfNeeded(
        sourceState: S,
        headers: Map<String, Any?>,
    ) {
        val autoTransition = transitionTable.findAutoTransition(sourceState)
        if (autoTransition != null) {
            log.debug("Executing auto transition: {} -> {}", sourceState, autoTransition.target)

            val context =
                StateContext(
                    machineId = id,
                    sourceState = sourceState,
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
                        id,
                        actionResult.reason,
                    )
                }

                is ActionResult.Success -> {
                    val newState = autoTransition.target
                    currentState = newState
                    repository?.save(this)
                    log.info("Auto transition: machineId={}, {} -> {}", id, sourceState, newState)

                    listener?.onStateChanged(
                        StateContext(
                            machineId = id,
                            sourceState = sourceState,
                            targetState = newState,
                            event = null,
                            headers = headers,
                            extendedState = extendedState,
                        ),
                    )

                    executeAutoTransitionIfNeeded(newState, headers)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(StateMachine::class.java)

        fun <S : Enum<S>> restore(
            id: String,
            currentState: S,
            initialState: S,
            extendedState: Map<String, Any?> = emptyMap(),
            transitionTable: TransitionTable<S> = TransitionTable(),
            listener: StateChangedListener<S>? = null,
            repository: StateMachineRepository<S>? = null,
        ): StateMachine<S> =
            StateMachine(
                id = id,
                currentState = currentState,
                initialState = initialState,
                extendedState = extendedState.toMutableMap(),
                transitionTable = transitionTable,
                listener = listener,
                repository = repository,
            )
    }
}

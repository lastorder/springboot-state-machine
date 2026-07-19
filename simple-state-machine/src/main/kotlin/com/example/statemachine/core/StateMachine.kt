package com.example.statemachine.core

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.StateChangeResult
import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.api.StateContext
import org.slf4j.LoggerFactory

class StateMachine<S : Enum<S>> internal constructor(
    val id: String,
    private var currentState: S,
    val initialState: S,
    private val extendedState: MutableMap<String, Any?>,
    private val transitionTable: TransitionTable<S>,
    private val listener: StateChangedListener<S>?,
) {
    val state: S get() = currentState

    fun sendEvent(
        event: Enum<*>,
        headers: Map<String, Any?> = emptyMap(),
    ): StateChangeResult<S> {
        log.debug("Sending event: machineId={}, event={}", id, event)
        log.debug("Current state: {}", currentState)

        val transition = transitionTable.findByEvent(currentState, event)
        if (transition == null) {
            log.warn("No transition found for state={}, event={}", currentState, event)
            return StateChangeResult.invalidTransition(currentState, event)
        }

        return executeTransition(currentState, transition, event, headers)
    }

    private fun executeTransition(
        sourceState: S,
        transition: Transition<S>,
        event: Enum<*>,
        headers: Map<String, Any?>,
    ): StateChangeResult<S> {
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
            is ActionResult.Failure.BusinessError -> {
                log.warn(
                    "Action failed (business error): machineId={}, reason={}",
                    id,
                    actionResult.reason,
                )
                StateChangeResult.businessError(sourceState, event)
            }

            is ActionResult.Failure.TechnicalError -> {
                log.error(
                    "Action failed (technical error): machineId={}, reason={}",
                    id,
                    actionResult.reason,
                    actionResult.cause,
                )
                StateChangeResult.technicalError(sourceState, event)
            }

            is ActionResult.Success -> {
                val newState = transition.target
                val previousState = currentState
                currentState = newState
                log.info("State changed: machineId={}, {} -> {}", id, previousState, newState)

                listener?.onStateChanged(
                    StateContext(
                        machineId = id,
                        sourceState = previousState,
                        targetState = newState,
                        event = event,
                        headers = headers,
                        extendedState = extendedState,
                    ),
                )

                executeAutoTransitionIfNeeded(newState, headers)

                actionResult.nextEvent?.let { sendEvent(it, headers) }
                    ?: StateChangeResult(
                        accepted = true,
                        previousState = previousState,
                        newState = currentState,
                        event = event,
                    )
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
                is ActionResult.Failure.BusinessError -> {
                    log.warn(
                        "Auto transition action failed (business error): machineId={}, reason={}",
                        id,
                        actionResult.reason,
                    )
                }

                is ActionResult.Failure.TechnicalError -> {
                    log.error(
                        "Auto transition action failed (technical error): machineId={}, reason={}",
                        id,
                        actionResult.reason,
                        actionResult.cause,
                    )
                }

                is ActionResult.Success -> {
                    val newState = autoTransition.target
                    val previousState = currentState
                    currentState = newState
                    log.info("Auto transition: machineId={}, {} -> {}", id, previousState, newState)

                    listener?.onStateChanged(
                        StateContext(
                            machineId = id,
                            sourceState = previousState,
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
        ): StateMachine<S> =
            StateMachine(
                id = id,
                currentState = currentState,
                initialState = initialState,
                extendedState = extendedState.toMutableMap(),
                transitionTable = transitionTable,
                listener = listener,
            )
    }
}

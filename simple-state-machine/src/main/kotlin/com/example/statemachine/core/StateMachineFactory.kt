package com.example.statemachine.core

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.StateChangedListener
import com.example.statemachine.api.StateContext
import com.example.statemachine.persistence.StateMachineRepository
import org.slf4j.LoggerFactory

class StateMachineFactory<S : Enum<S>>(
    private val initialState: S,
    private val transitionTable: TransitionTable<S>,
    private val listener: StateChangedListener<S>?,
    private val repository: StateMachineRepository<S>,
) {
    fun create(id: String): StateMachine<S> {
        val existing = repository.findById(id)
        return existing ?: StateMachine.restore(
            id = id,
            currentState = initialState,
            initialState = initialState,
            transitionTable = transitionTable,
            listener = listener,
            repository = repository,
        )
    }

    fun getState(id: String): S = repository.findById(id)?.state ?: initialState

    class Builder<S : Enum<S>> {
        var initialState: S? = null
        private val transitionTable = TransitionTable<S>()
        var listener: StateChangedListener<S>? = null
        var repository: StateMachineRepository<S>? = null

        fun transition(block: TransitionBuilder<S>.() -> Unit) {
            val builder = TransitionBuilder<S>()
            builder.block()
            transitionTable.add(builder.build())
        }

        fun build(): StateMachineFactory<S> {
            checkNotNull(initialState) { "Initial state must be set" }
            checkNotNull(repository) { "Repository must be set" }
            return StateMachineFactory(initialState!!, transitionTable, listener, repository!!)
        }
    }

    class TransitionBuilder<S : Enum<S>> {
        private var source: S? = null
        private var target: S? = null
        private var event: Enum<*>? = null
        private var action: Action<S>? = null

        fun from(state: S) = apply { source = state }

        fun to(state: S) = apply { target = state }

        fun on(e: Enum<*>) = apply { event = e }

        fun action(a: Action<S>) = apply { action = a }

        fun action(block: (StateContext<S>) -> ActionResult) =
            apply {
                action =
                    object : Action<S> {
                        override fun execute(context: StateContext<S>): ActionResult = block(context)
                    }
            }

        fun build(): Transition<S> {
            checkNotNull(source) { "Source state must be set" }
            checkNotNull(target) { "Target state must be set" }
            return Transition(source!!, target!!, event, action)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(StateMachineFactory::class.java)
    }
}

fun <S : Enum<S>> stateMachineFactory(block: StateMachineFactory.Builder<S>.() -> Unit): StateMachineFactory<S> {
    val builder = StateMachineFactory.Builder<S>()
    builder.block()
    return builder.build()
}

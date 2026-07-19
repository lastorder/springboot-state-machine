package com.example.statemachine.core

import com.example.statemachine.api.Event
import com.example.statemachine.api.State

class TransitionTable<S : State, E : Event> {
    private val eventTransitions: MutableMap<Pair<S, E>, Transition<S, E>> = mutableMapOf()
    private val autoTransitions: MutableMap<S, Transition<S, E>> = mutableMapOf()

    fun add(transition: Transition<S, E>) {
        if (transition.event != null) {
            eventTransitions[transition.source to transition.event] = transition
        } else {
            autoTransitions[transition.source] = transition
        }
    }

    fun findByEvent(
        source: S,
        event: E,
    ): Transition<S, E>? = eventTransitions[source to event]

    fun findAutoTransition(source: S): Transition<S, E>? = autoTransitions[source]

    fun allTransitions(): List<Transition<S, E>> =
        eventTransitions.values.toList() + autoTransitions.values.toList()
}

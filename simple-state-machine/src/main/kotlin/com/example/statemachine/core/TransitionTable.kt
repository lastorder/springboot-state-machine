package com.example.statemachine.core

class TransitionTable<S : Enum<S>> {
    private val eventTransitions: MutableMap<Pair<S, Enum<*>>, Transition<S>> = mutableMapOf()
    private val autoTransitions: MutableMap<S, Transition<S>> = mutableMapOf()

    fun add(transition: Transition<S>) {
        if (transition.event != null) {
            eventTransitions[transition.source to transition.event] = transition
        } else {
            autoTransitions[transition.source] = transition
        }
    }

    fun findByEvent(
        source: S,
        event: Enum<*>,
    ): Transition<S>? = eventTransitions[source to event]

    fun findAutoTransition(source: S): Transition<S>? = autoTransitions[source]

    fun allTransitions(): List<Transition<S>> =
        eventTransitions.values.toList() + autoTransitions.values.toList()
}

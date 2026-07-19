package com.example.statemachine.core

import com.example.statemachine.api.Action

data class Transition<S : Enum<S>>(
    val source: S,
    val target: S,
    val event: Enum<*>?,
    val action: Action<S>? = null,
) {
    class Builder<S : Enum<S>> {
        private var source: S? = null
        private var target: S? = null
        private var event: Enum<*>? = null
        private var action: Action<S>? = null

        fun from(state: S) = apply { source = state }

        fun to(state: S) = apply { target = state }

        fun on(e: Enum<*>) = apply { event = e }

        fun action(a: Action<S>) = apply { action = a }

        fun build(): Transition<S> {
            checkNotNull(source) { "Source state must be set" }
            checkNotNull(target) { "Target state must be set" }
            return Transition(source!!, target!!, event, action)
        }
    }
}

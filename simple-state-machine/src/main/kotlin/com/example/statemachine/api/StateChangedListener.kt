package com.example.statemachine.api

interface StateChangedListener<S : State, E : Event> {
    fun onStateChanged(context: StateContext<S, E>)
}

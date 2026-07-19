package com.example.statemachine.api

interface StateChangedListener<S : Enum<S>> {
    fun onStateChanged(context: StateContext<S>)
}

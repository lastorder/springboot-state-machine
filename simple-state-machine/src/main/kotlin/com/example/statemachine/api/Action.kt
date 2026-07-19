package com.example.statemachine.api

interface Action<S : State, E : Event> {
    fun execute(context: StateContext<S, E>): ActionResult<E>
}

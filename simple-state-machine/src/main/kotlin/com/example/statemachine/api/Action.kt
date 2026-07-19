package com.example.statemachine.api

interface Action<S : Enum<S>> {
    fun execute(context: StateContext<S>): ActionResult
}

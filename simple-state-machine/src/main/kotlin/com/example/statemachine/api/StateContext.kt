package com.example.statemachine.api

data class StateContext<S : State, E : Event>(
    val machineId: String,
    val sourceState: S,
    val targetState: S,
    val event: E?,
    val headers: Map<String, Any?> = emptyMap(),
    val extendedState: MutableMap<String, Any?> = mutableMapOf(),
)

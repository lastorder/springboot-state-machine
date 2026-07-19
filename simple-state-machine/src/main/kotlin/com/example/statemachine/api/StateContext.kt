package com.example.statemachine.api

data class StateContext<S : Enum<S>>(
    val machineId: String,
    val sourceState: S,
    val targetState: S,
    val event: Enum<*>?,
    val headers: Map<String, Any?> = emptyMap(),
    val extendedState: MutableMap<String, Any?> = mutableMapOf(),
)

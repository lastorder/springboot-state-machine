package com.example.statemachine.core

import com.example.statemachine.api.Action
import com.example.statemachine.api.Event
import com.example.statemachine.api.State

data class Transition<S : State, E : Event>(
    val source: S,
    val target: S,
    val event: E?,
    val action: Action<S, E>? = null,
)

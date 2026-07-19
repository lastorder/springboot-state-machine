package com.example.statemachine.core

import com.example.statemachine.api.Action

data class Transition<S : Enum<S>>(
    val source: S,
    val target: S,
    val event: Enum<*>?,
    val action: Action<S>? = null,
)

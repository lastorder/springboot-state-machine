package com.example.statemachine.exception

class InvalidTransitionException(
    message: String,
) : RuntimeException(message)

class StateMachineException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

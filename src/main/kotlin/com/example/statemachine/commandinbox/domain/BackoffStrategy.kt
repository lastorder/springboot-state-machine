package com.example.statemachine.commandinbox.domain

enum class BackoffStrategy {
    FIXED,
    EXPONENTIAL,
    LINEAR,
}

package com.example.statemachine.commandinbox.domain

enum class CommandStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    SKIPPED,
    EXPIRED,
    DEAD,
}

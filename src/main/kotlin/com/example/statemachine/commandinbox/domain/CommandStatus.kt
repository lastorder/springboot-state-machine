package com.example.statemachine.commandinbox.domain

enum class CommandStatus {
    PENDING,
    PROCESSING,
    RETRYING,
    COMPLETED,
    FAILED,
    SKIPPED,
    EXPIRED,
}

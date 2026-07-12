package com.example.statemachine.commandinbox.domain

enum class CommandSource {
    HTTP,
    KAFKA,
    SCHEDULED,
    INTERNAL,
}

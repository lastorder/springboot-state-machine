package com.example.statemachine.commandinbox.domain

enum class CommandPriority(
    val value: Int,
) {
    URGENT(200),
    HIGH(100),
    NORMAL(0),
}

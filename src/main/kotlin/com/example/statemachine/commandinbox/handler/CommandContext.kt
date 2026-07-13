package com.example.statemachine.commandinbox.handler

data class CommandContext<P>(
    val commandId: Long,
    val groupId: String,
    val payload: P,
    val metadata: Map<String, Any?>,
    val retryCount: Int,
)

package com.example.statemachine.commandinbox.dto

import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.domain.enums.OrderEvent
import java.time.Instant

data class CommandSubmitResult(
    val commandId: Long,
    val orderId: Long,
    val status: CommandStatus,
    val message: String,
)

data class CommandStatusResponse(
    val commandId: Long,
    val orderId: Long,
    val eventType: OrderEvent,
    val status: CommandStatus,
    val errorMessage: String?,
    val createdAt: Instant,
    val processedAt: Instant?,
)

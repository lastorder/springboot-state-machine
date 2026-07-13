package com.example.statemachine.commandinbox.dto

import com.example.statemachine.commandinbox.domain.CommandStatus
import java.time.Instant

data class CommandSubmitResult(
    val commandId: Long,
    val groupId: String,
    val commandType: String,
    val status: CommandStatus,
    val message: String,
)

data class CommandStatusResponse(
    val commandId: Long,
    val groupId: String,
    val commandType: String,
    val status: CommandStatus,
    val retryCount: Int,
    val maxRetries: Int,
    val errorMessage: String?,
    val response: String?,
    val createdAt: Instant,
    val processedAt: Instant?,
    val nextRetryAt: Instant?,
)

data class BackoffConfig(
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 60000,
    val multiplier: Double = 2.0,
) {
    companion object {
        val DEFAULT = BackoffConfig()
    }
}

data class CommandMetadata(
    val traceId: String? = null,
    val spanId: String? = null,
    val correlationId: String? = null,
    val source: String? = null,
    val sourceReference: String? = null,
    val custom: Map<String, Any?>? = null,
)

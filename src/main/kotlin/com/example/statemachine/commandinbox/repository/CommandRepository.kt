package com.example.statemachine.commandinbox.repository

import com.example.statemachine.commandinbox.domain.Command
import com.example.statemachine.commandinbox.domain.CommandStatus
import java.time.Instant

interface CommandRepository {
    fun save(command: Command): Command

    fun findById(id: Long): Command?

    fun findByIdAndGroupId(
        id: Long,
        groupId: String,
    ): Command?

    fun findNextPendingCommand(
        groupId: String,
        now: Instant = Instant.now(),
    ): Command?

    fun findNextRetryableCommand(
        groupId: String,
        now: Instant = Instant.now(),
    ): Command?

    fun existsByIdempotencyKey(
        groupId: String,
        commandType: String,
        idempotencyKey: String,
    ): Boolean

    fun countPendingCommands(
        groupId: String,
        now: Instant = Instant.now(),
    ): Long

    fun deleteByStatusAndProcessedAtBefore(
        status: CommandStatus,
        cutoffDate: Instant,
    ): Int

    fun updateStatus(
        id: Long,
        status: CommandStatus,
        errorMessage: String? = null,
        response: String? = null,
        processedAt: Instant? = null,
        nextRetryAt: Instant? = null,
    ): Int

    fun incrementRetryCount(
        id: Long,
        nextRetryAt: Instant,
    ): Int
}

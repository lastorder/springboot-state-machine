package com.example.statemachine.commandinbox.repository

import com.example.statemachine.commandinbox.domain.CommandInbox
import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.domain.enums.OrderEvent
import java.time.Instant

interface CommandInboxRepository {
    fun save(command: CommandInbox): CommandInbox

    fun findById(id: Long): CommandInbox?

    fun findNextPendingCommand(
        orderId: Long,
        now: Instant = Instant.now(),
    ): CommandInbox?

    fun existsByIdempotencyKey(
        orderId: Long,
        eventType: OrderEvent,
        idempotencyKey: String,
    ): Boolean

    fun findExpiredCommands(now: Instant): List<CommandInbox>

    fun countPendingCommands(
        orderId: Long,
        now: Instant = Instant.now(),
    ): Long

    fun findByIdAndOrderId(
        id: Long,
        orderId: Long,
    ): CommandInbox?

    fun deleteByStatusAndProcessedAtBefore(
        status: CommandStatus,
        cutoffDate: Instant,
    ): Int
}

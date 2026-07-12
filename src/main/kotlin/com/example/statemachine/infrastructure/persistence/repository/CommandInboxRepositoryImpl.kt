package com.example.statemachine.infrastructure.persistence.repository

import com.example.statemachine.commandinbox.domain.CommandInbox
import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.commandinbox.repository.CommandInboxRepository
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.persistence.converter.CommandInboxConverter
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class CommandInboxRepositoryImpl(
    private val commandInboxJpaRepository: CommandInboxJpaRepository,
) : CommandInboxRepository {
    override fun save(command: CommandInbox): CommandInbox {
        val entity = CommandInboxConverter.toEntity(command)
        val savedEntity = commandInboxJpaRepository.save(entity)
        return CommandInboxConverter.toDomain(savedEntity)
    }

    override fun findById(id: Long): CommandInbox? =
        commandInboxJpaRepository
            .findById(id)
            .map { CommandInboxConverter.toDomain(it) }
            .orElse(null)

    override fun findNextPendingCommand(
        orderId: Long,
        now: Instant,
    ): CommandInbox? =
        commandInboxJpaRepository
            .findNextPendingCommand(orderId, now)
            ?.let { CommandInboxConverter.toDomain(it) }

    override fun existsByIdempotencyKey(
        orderId: Long,
        eventType: OrderEvent,
        idempotencyKey: String,
    ): Boolean = commandInboxJpaRepository.existsByIdempotencyKey(orderId, eventType, idempotencyKey)

    override fun findExpiredCommands(now: Instant): List<CommandInbox> =
        commandInboxJpaRepository
            .findExpiredCommands(now)
            .map { CommandInboxConverter.toDomain(it) }

    override fun countPendingCommands(
        orderId: Long,
        now: Instant,
    ): Long = commandInboxJpaRepository.countPendingCommands(orderId, now)

    override fun findByIdAndOrderId(
        id: Long,
        orderId: Long,
    ): CommandInbox? =
        commandInboxJpaRepository
            .findByIdAndOrderId(id, orderId)
            ?.let { CommandInboxConverter.toDomain(it) }

    override fun deleteByStatusAndProcessedAtBefore(
        status: CommandStatus,
        cutoffDate: Instant,
    ): Int = commandInboxJpaRepository.deleteByStatusAndProcessedAtBefore(status, cutoffDate)
}

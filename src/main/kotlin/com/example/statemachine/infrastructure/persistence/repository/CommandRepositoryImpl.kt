package com.example.statemachine.infrastructure.persistence.repository

import com.example.statemachine.commandinbox.domain.Command
import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.commandinbox.repository.CommandRepository
import com.example.statemachine.infrastructure.persistence.converter.CommandConverter
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class CommandRepositoryImpl(
    private val commandJpaRepository: CommandJpaRepository,
) : CommandRepository {
    override fun save(command: Command): Command {
        val entity = CommandConverter.toEntity(command)
        val savedEntity = commandJpaRepository.save(entity)
        return CommandConverter.toDomain(savedEntity)
    }

    override fun findById(id: Long): Command? =
        commandJpaRepository
            .findById(id)
            .map { CommandConverter.toDomain(it) }
            .orElse(null)

    override fun findByIdAndGroupId(
        id: Long,
        groupId: String,
    ): Command? =
        commandJpaRepository
            .findByIdAndGroupId(id, groupId)
            ?.let { CommandConverter.toDomain(it) }

    override fun findNextPendingCommand(
        groupId: String,
        now: Instant,
    ): Command? =
        commandJpaRepository
            .findNextPendingCommand(groupId, now)
            ?.let { CommandConverter.toDomain(it) }

    override fun findNextRetryableCommand(
        groupId: String,
        now: Instant,
    ): Command? =
        commandJpaRepository
            .findNextRetryableCommand(groupId, now)
            ?.let { CommandConverter.toDomain(it) }

    override fun existsByIdempotencyKey(
        groupId: String,
        commandType: String,
        idempotencyKey: String,
    ): Boolean = commandJpaRepository.existsByIdempotencyKey(groupId, commandType, idempotencyKey)

    override fun countPendingCommands(
        groupId: String,
        now: Instant,
    ): Long = commandJpaRepository.countPendingCommands(groupId, now)

    override fun deleteByStatusAndProcessedAtBefore(
        status: CommandStatus,
        cutoffDate: Instant,
    ): Int = commandJpaRepository.deleteByStatusAndProcessedAtBefore(status, cutoffDate)

    override fun updateStatus(
        id: Long,
        status: CommandStatus,
        errorMessage: String?,
        response: String?,
        processedAt: Instant?,
        nextRetryAt: Instant?,
    ): Int = commandJpaRepository.updateStatus(id, status, errorMessage, response, processedAt, nextRetryAt)

    override fun incrementRetryCount(
        id: Long,
        nextRetryAt: Instant,
    ): Int = commandJpaRepository.incrementRetryCount(id, nextRetryAt)
}

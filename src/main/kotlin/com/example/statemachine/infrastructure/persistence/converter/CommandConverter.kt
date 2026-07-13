package com.example.statemachine.infrastructure.persistence.converter

import com.example.statemachine.commandinbox.domain.Command
import com.example.statemachine.infrastructure.persistence.entity.CommandJpaEntity

object CommandConverter {
    fun toEntity(command: Command): CommandJpaEntity =
        CommandJpaEntity(
            id = command.id,
            groupId = command.groupId,
            commandType = command.commandType,
            idempotencyKey = command.idempotencyKey,
            payload = command.payload,
            response = command.response,
            metadata = command.metadata,
            priority = command.priority,
            maxRetries = command.maxRetries,
            retryCount = command.retryCount,
            backoffStrategy = command.backoffStrategy,
            backoffConfig = command.backoffConfig,
            status = command.status,
            errorMessage = command.errorMessage,
            createdAt = command.createdAt,
            updatedAt = command.updatedAt,
            processedAt = command.processedAt,
            nextRetryAt = command.nextRetryAt,
        )

    fun toDomain(entity: CommandJpaEntity): Command =
        Command(
            id = entity.id,
            groupId = entity.groupId,
            commandType = entity.commandType,
            idempotencyKey = entity.idempotencyKey,
            payload = entity.payload,
            response = entity.response,
            metadata = entity.metadata,
            priority = entity.priority,
            maxRetries = entity.maxRetries,
            retryCount = entity.retryCount,
            backoffStrategy = entity.backoffStrategy,
            backoffConfig = entity.backoffConfig,
            status = entity.status,
            errorMessage = entity.errorMessage,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            processedAt = entity.processedAt,
            nextRetryAt = entity.nextRetryAt,
        )
}

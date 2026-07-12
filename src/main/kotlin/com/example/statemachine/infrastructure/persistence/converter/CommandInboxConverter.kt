package com.example.statemachine.infrastructure.persistence.converter

import com.example.statemachine.commandinbox.domain.CommandInbox
import com.example.statemachine.infrastructure.persistence.entity.CommandInboxJpaEntity

object CommandInboxConverter {
    fun toEntity(command: CommandInbox): CommandInboxJpaEntity =
        CommandInboxJpaEntity(
            id = command.id,
            orderId = command.orderId,
            eventType = command.eventType,
            source = command.source,
            sourceReference = command.sourceReference,
            correlationId = command.correlationId,
            payload = command.payload,
            headers = command.headers,
            idempotencyKey = command.idempotencyKey,
            priority = command.priority,
            expiresAt = command.expiresAt,
            status = command.status,
            errorMessage = command.errorMessage,
            createdAt = command.createdAt,
            updatedAt = command.updatedAt,
            processedAt = command.processedAt,
        )

    fun toDomain(entity: CommandInboxJpaEntity): CommandInbox =
        CommandInbox(
            id = entity.id,
            orderId = entity.orderId,
            eventType = entity.eventType,
            source = entity.source,
            sourceReference = entity.sourceReference,
            correlationId = entity.correlationId,
            payload = entity.payload,
            headers = entity.headers,
            idempotencyKey = entity.idempotencyKey,
            priority = entity.priority,
            expiresAt = entity.expiresAt,
            status = entity.status,
            errorMessage = entity.errorMessage,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            processedAt = entity.processedAt,
        )
}

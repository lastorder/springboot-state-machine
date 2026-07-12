package com.example.statemachine.commandinbox.service

import com.example.statemachine.commandinbox.domain.CommandInbox
import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.domain.CommandSource
import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.commandinbox.dto.CommandSubmitResult
import com.example.statemachine.commandinbox.exception.DuplicateCommandException
import com.example.statemachine.commandinbox.exception.ExpiredCommandException
import com.example.statemachine.commandinbox.repository.CommandInboxRepository
import com.example.statemachine.commandinbox.scheduler.OrderTaskData
import com.example.statemachine.domain.enums.OrderEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.TaskInstance
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class CommandInboxService(
    private val commandInboxRepository: CommandInboxRepository,
    private val schedulerClient: SchedulerClient,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CommandInboxService::class.java)
        const val TASK_NAME = "order-state-machine"
    }

    fun submitCommand(
        orderId: Long,
        event: OrderEvent,
        source: CommandSource,
        payload: Map<String, Any>? = null,
        headers: Map<String, Any>? = null,
        idempotencyKey: String? = null,
        priority: CommandPriority = CommandPriority.NORMAL,
        expiresAt: Instant? = null,
        correlationId: String? = null,
        sourceReference: String? = null,
    ): CommandSubmitResult {
        log.info(
            "Submitting command: orderId={}, event={}, source={}, priority={}",
            orderId,
            event,
            source,
            priority,
        )

        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            log.warn("Command already expired: orderId={}, event={}", orderId, event)
            throw ExpiredCommandException("Command is already expired")
        }

        val finalIdempotencyKey = idempotencyKey ?: generateIdempotencyKey(orderId, event)

        if (commandInboxRepository.existsByIdempotencyKey(orderId, event, finalIdempotencyKey)) {
            log.info(
                "Duplicate command detected: orderId={}, event={}, key={}",
                orderId,
                event,
                finalIdempotencyKey,
            )
            throw DuplicateCommandException("Command already exists with idempotency key: $finalIdempotencyKey")
        }

        var command =
            CommandInbox(
                orderId = orderId,
                eventType = event,
                source = source,
                sourceReference = sourceReference,
                correlationId = correlationId,
                payload = payload?.let { objectMapper.writeValueAsString(it) },
                headers = headers?.let { objectMapper.writeValueAsString(it) },
                idempotencyKey = finalIdempotencyKey,
                priority = priority.value,
                expiresAt = expiresAt,
            )

        command = commandInboxRepository.save(command)
        log.info("Command saved: id={}, orderId={}, event={}", command.id, orderId, event)

        scheduleOrderTask(orderId)

        return CommandSubmitResult(
            commandId = command.id!!,
            orderId = orderId,
            status = CommandStatus.PENDING,
            message = "Command accepted for processing",
        )
    }

    fun markCompleted(commandId: Long) {
        val command = commandInboxRepository.findById(commandId) ?: throw IllegalArgumentException("Command not found: $commandId")
        val updatedCommand =
            CommandInbox(
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
                status = CommandStatus.COMPLETED,
                errorMessage = command.errorMessage,
                createdAt = command.createdAt,
                updatedAt = Instant.now(),
                processedAt = Instant.now(),
            )
        commandInboxRepository.save(updatedCommand)
        log.info("Command completed: id={}, orderId={}", commandId, updatedCommand.orderId)
    }

    fun markSkipped(
        commandId: Long,
        reason: String,
    ) {
        val command = commandInboxRepository.findById(commandId) ?: throw IllegalArgumentException("Command not found: $commandId")
        val updatedCommand =
            CommandInbox(
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
                status = CommandStatus.SKIPPED,
                errorMessage = reason.take(500),
                createdAt = command.createdAt,
                updatedAt = Instant.now(),
                processedAt = Instant.now(),
            )
        commandInboxRepository.save(updatedCommand)
        log.warn(
            "Command marked as SKIPPED: id={}, orderId={}, reason={}",
            commandId,
            updatedCommand.orderId,
            reason,
        )
    }

    fun markExpired(commandId: Long) {
        val command = commandInboxRepository.findById(commandId) ?: throw IllegalArgumentException("Command not found: $commandId")
        val updatedCommand =
            CommandInbox(
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
                status = CommandStatus.EXPIRED,
                errorMessage = command.errorMessage,
                createdAt = command.createdAt,
                updatedAt = Instant.now(),
                processedAt = command.processedAt,
            )
        commandInboxRepository.save(updatedCommand)
        log.info("Command expired: id={}, orderId={}", commandId, updatedCommand.orderId)
    }

    fun getCommandStatus(
        orderId: Long,
        commandId: Long,
    ): CommandInbox? = commandInboxRepository.findByIdAndOrderId(commandId, orderId)

    private fun scheduleOrderTask(orderId: Long) {
        val taskInstance =
            TaskInstance<OrderTaskData>(
                TASK_NAME,
                orderId.toString(),
                OrderTaskData(orderId),
            )

        try {
            schedulerClient.scheduleIfNotExists(taskInstance, Instant.now())
            log.debug("Scheduled order task: orderId={}", orderId)
        } catch (e: Exception) {
            log.debug("Order task already scheduled or exists: orderId={}", orderId)
        }
    }

    private fun generateIdempotencyKey(
        orderId: Long,
        event: OrderEvent,
    ): String {
        val timestamp = Instant.now().toEpochMilli()
        return "$orderId-$event-$timestamp"
    }
}

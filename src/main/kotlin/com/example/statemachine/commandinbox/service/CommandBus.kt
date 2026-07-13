package com.example.statemachine.commandinbox.service

import com.example.statemachine.commandinbox.domain.BackoffStrategy
import com.example.statemachine.commandinbox.domain.Command
import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.commandinbox.dto.BackoffConfig
import com.example.statemachine.commandinbox.dto.CommandMetadata
import com.example.statemachine.commandinbox.dto.CommandSubmitResult
import com.example.statemachine.commandinbox.exception.DuplicateCommandException
import com.example.statemachine.commandinbox.handler.CommandSpec
import com.example.statemachine.commandinbox.repository.CommandRepository
import com.example.statemachine.commandinbox.scheduler.CommandTaskData
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.TaskInstance
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class CommandBus(
    private val commandRepository: CommandRepository,
    private val schedulerClient: SchedulerClient,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CommandBus::class.java)
        const val TASK_NAME = "command-inbox-task"
    }

    fun <P : Any> submit(
        groupId: String,
        commandType: String,
        payload: P? = null,
        metadata: CommandMetadata? = null,
        idempotencyKey: String? = null,
        priority: CommandPriority = CommandPriority.NORMAL,
        maxRetries: Int = 3,
        backoffStrategy: BackoffStrategy = BackoffStrategy.FIXED,
        backoffConfig: BackoffConfig? = null,
    ): CommandSubmitResult {
        log.info(
            "Submitting command: groupId={}, commandType={}, priority={}",
            groupId,
            commandType,
            priority,
        )

        val finalIdempotencyKey = idempotencyKey ?: generateIdempotencyKey(groupId, commandType)

        if (commandRepository.existsByIdempotencyKey(groupId, commandType, finalIdempotencyKey)) {
            log.info(
                "Duplicate command detected: groupId={}, commandType={}, key={}",
                groupId,
                commandType,
                finalIdempotencyKey,
            )
            throw DuplicateCommandException("Command already exists with idempotency key: $finalIdempotencyKey")
        }

        var command =
            Command(
                groupId = groupId,
                commandType = commandType,
                idempotencyKey = finalIdempotencyKey,
                payload = payload?.let { objectMapper.writeValueAsString(it) },
                metadata = metadata?.let { objectMapper.writeValueAsString(it) },
                priority = priority.value,
                maxRetries = maxRetries,
                backoffStrategy = backoffStrategy,
                backoffConfig = backoffConfig?.let { objectMapper.writeValueAsString(it) },
            )

        command = commandRepository.save(command)
        log.info("Command saved: id={}, groupId={}, commandType={}", command.id, groupId, commandType)

        scheduleCommandTask(groupId)

        return CommandSubmitResult(
            commandId = command.id!!,
            groupId = groupId,
            commandType = commandType,
            status = CommandStatus.PENDING,
            message = "Command accepted for processing",
        )
    }

    fun <P : Any, R : Any> submit(
        spec: CommandSpec<P, R>,
        groupId: String,
        payload: P,
        metadata: CommandMetadata? = null,
        idempotencyKey: String? = null,
        priority: CommandPriority = spec.defaultPriority,
    ): CommandSubmitResult =
        submit(
            groupId = groupId,
            commandType = spec.commandType,
            payload = payload,
            metadata = metadata,
            idempotencyKey = idempotencyKey,
            priority = priority,
            maxRetries = spec.defaultMaxRetries,
            backoffStrategy = spec.defaultBackoffStrategy,
            backoffConfig = spec.defaultBackoffConfig,
        )

    fun markCompleted(
        commandId: Long,
        response: Any? = null,
    ) {
        val responseJson = response?.let { objectMapper.writeValueAsString(it) }
        commandRepository.updateStatus(
            id = commandId,
            status = CommandStatus.COMPLETED,
            response = responseJson,
            processedAt = Instant.now(),
        )
        log.info("Command completed: id={}", commandId)
    }

    fun markSkipped(
        commandId: Long,
        reason: String,
    ) {
        commandRepository.updateStatus(
            id = commandId,
            status = CommandStatus.SKIPPED,
            errorMessage = reason.take(500),
            processedAt = Instant.now(),
        )
        log.warn("Command marked as SKIPPED: id={}, reason={}", commandId, reason)
    }

    fun markFailed(
        commandId: Long,
        error: String,
    ) {
        commandRepository.updateStatus(
            id = commandId,
            status = CommandStatus.FAILED,
            errorMessage = error.take(500),
            processedAt = Instant.now(),
        )
        log.error("Command marked as FAILED: id={}, error={}", commandId, error)
    }

    fun scheduleRetry(
        command: Command,
        error: String,
    ) {
        val nextRetryAt = calculateNextRetryAt(command)
        if (nextRetryAt == null) {
            markFailed(command.id!!, error)
            return
        }

        commandRepository.updateStatus(
            id = command.id!!,
            status = CommandStatus.RETRYING,
            errorMessage = error.take(500),
            nextRetryAt = nextRetryAt,
        )
        log.info(
            "Command scheduled for retry: id={}, retryCount={}, nextRetryAt={}",
            command.id,
            command.retryCount + 1,
            nextRetryAt,
        )
    }

    fun incrementRetryCount(command: Command) {
        val nextRetryAt = calculateNextRetryAt(command)
        if (nextRetryAt != null) {
            commandRepository.incrementRetryCount(command.id!!, nextRetryAt)
        }
    }

    fun markExpired(commandId: Long) {
        commandRepository.updateStatus(
            id = commandId,
            status = CommandStatus.EXPIRED,
            processedAt = Instant.now(),
        )
        log.info("Command expired: id={}", commandId)
    }

    fun getCommandStatus(
        groupId: String,
        commandId: Long,
    ): Command? = commandRepository.findByIdAndGroupId(commandId, groupId)

    private fun calculateNextRetryAt(command: Command): Instant? {
        if (command.retryCount >= command.maxRetries) {
            return null
        }

        val config = parseBackoffConfig(command.backoffConfig)
        val delayMs =
            when (command.backoffStrategy) {
                BackoffStrategy.FIXED -> config.initialDelayMs
                BackoffStrategy.LINEAR -> config.initialDelayMs * (command.retryCount + 1)
                BackoffStrategy.EXPONENTIAL -> {
                    val delay = config.initialDelayMs * Math.pow(config.multiplier, command.retryCount.toDouble())
                    delay.toLong().coerceAtMost(config.maxDelayMs)
                }
            }

        return Instant.now().plusMillis(delayMs)
    }

    private fun parseBackoffConfig(configJson: String?): BackoffConfig {
        if (configJson == null) return BackoffConfig.DEFAULT
        return try {
            objectMapper.readValue(configJson, BackoffConfig::class.java)
        } catch (e: Exception) {
            BackoffConfig.DEFAULT
        }
    }

    private fun scheduleCommandTask(groupId: String) {
        val taskInstance =
            TaskInstance<CommandTaskData>(
                TASK_NAME,
                groupId,
                CommandTaskData(groupId),
            )

        try {
            schedulerClient.scheduleIfNotExists(taskInstance, Instant.now())
            log.debug("Scheduled command task: groupId={}", groupId)
        } catch (e: Exception) {
            log.debug("Command task already scheduled or exists: groupId={}", groupId)
        }
    }

    private fun generateIdempotencyKey(
        groupId: String,
        commandType: String,
    ): String {
        val timestamp = Instant.now().toEpochMilli()
        return "$groupId-$commandType-$timestamp"
    }
}

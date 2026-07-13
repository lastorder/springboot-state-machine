package com.example.statemachine.commandinbox.scheduler

import com.example.statemachine.commandinbox.domain.Command
import com.example.statemachine.commandinbox.handler.CommandContext
import com.example.statemachine.commandinbox.handler.CommandResult
import com.example.statemachine.commandinbox.handler.CommandSpec
import com.example.statemachine.commandinbox.handler.CommandSpecRegistry
import com.example.statemachine.commandinbox.repository.CommandRepository
import com.example.statemachine.commandinbox.service.CommandBus
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.ExecutionContext
import com.github.kagkarlsson.scheduler.task.FailureHandler
import com.github.kagkarlsson.scheduler.task.TaskInstance
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

@Configuration
class CommandTaskConfig(
    private val commandBus: CommandBus,
    private val commandRepository: CommandRepository,
    private val specRegistry: CommandSpecRegistry,
    private val objectMapper: ObjectMapper,
    private val schedulerClient: SchedulerClient,
) {
    private val log = LoggerFactory.getLogger(CommandTaskConfig::class.java)

    companion object {
        const val NEXT_COMMAND_DELAY_MS = 100L
    }

    @Bean
    fun commandInboxTask(): OneTimeTask<CommandTaskData> {
        return object : OneTimeTask<CommandTaskData>(
            CommandBus.TASK_NAME,
            CommandTaskData::class.java,
            FailureHandler.OnFailureRetryLater(Duration.ofMinutes(1)),
        ) {
            override fun executeOnce(
                taskInstance: TaskInstance<CommandTaskData>,
                executionContext: ExecutionContext,
            ) {
                val groupId = taskInstance.data.groupId
                log.debug("Processing command task: groupId={}", groupId)

                val now = Instant.now()

                val retryCommand = commandRepository.findNextRetryableCommand(groupId, now)
                if (retryCommand != null) {
                    processCommand(retryCommand, groupId)
                } else {
                    val pendingCommand = commandRepository.findNextPendingCommand(groupId, now)
                    if (pendingCommand != null) {
                        processCommand(pendingCommand, groupId)
                    } else {
                        log.debug("No pending or retryable commands, task ending: groupId={}", groupId)
                        return
                    }
                }

                scheduleSelfIfHasPendingCommands(groupId)
            }

            private fun processCommand(
                command: Command,
                groupId: String,
            ) {
                val commandId = command.id!!
                log.info(
                    "Processing command: commandId={}, groupId={}, commandType={}, retryCount={}",
                    commandId,
                    groupId,
                    command.commandType,
                    command.retryCount,
                )

                try {
                    val spec = specRegistry.getSpec(command.commandType)
                    if (spec == null) {
                        commandBus.markSkipped(commandId, "No spec registered for command type: ${command.commandType}")
                        log.error("No spec for command type: ${command.commandType}")
                        return
                    }

                    executeSpecAndHandleResult(spec, command, groupId, commandId)
                } catch (e: Exception) {
                    handleCommandException(command, e)
                }
            }

            private fun executeSpecAndHandleResult(
                spec: CommandSpec<*, *>,
                command: Command,
                groupId: String,
                commandId: Long,
            ) {
                val payload = parsePayload(command.payload, spec.payloadType)
                val metadata = parseMetadata(command.metadata)

                val context =
                    CommandContext(
                        commandId = commandId,
                        groupId = groupId,
                        payload = payload,
                        metadata = metadata,
                        retryCount = command.retryCount,
                    )

                @Suppress("UNCHECKED_CAST")
                val typedSpec = spec as CommandSpec<Any, Any>
                val result = typedSpec.handle(context)

                when (result) {
                    is CommandResult.Success -> {
                        commandBus.markCompleted(commandId, result.response)
                        log.info("Command completed: commandId={}, groupId={}", commandId, groupId)
                    }
                    is CommandResult.Failure -> {
                        if (result.retryable && command.retryCount < command.maxRetries) {
                            commandBus.scheduleRetry(command, result.error)
                        } else {
                            commandBus.markFailed(commandId, result.error)
                        }
                    }
                    is CommandResult.Skipped -> {
                        commandBus.markSkipped(commandId, result.reason)
                    }
                }
            }

            private fun handleCommandException(
                command: Command,
                e: Exception,
            ) {
                log.error(
                    "Command processing exception: commandId={}, groupId={}, error={}",
                    command.id,
                    command.groupId,
                    e.message,
                    e,
                )

                if (command.retryCount < command.maxRetries) {
                    commandBus.scheduleRetry(command, e.message ?: "Unknown error")
                } else {
                    commandBus.markFailed(command.id!!, e.message ?: "Unknown error")
                }
            }

            private fun scheduleSelfIfHasPendingCommands(groupId: String) {
                Thread.sleep(NEXT_COMMAND_DELAY_MS)

                val now = Instant.now()
                val pendingCount = commandRepository.countPendingCommands(groupId, now)

                val hasRetryable = commandRepository.findNextRetryableCommand(groupId, now) != null

                if (pendingCount > 0 || hasRetryable) {
                    val taskInstance = TaskInstance(CommandBus.TASK_NAME, groupId, CommandTaskData(groupId))
                    schedulerClient.scheduleIfNotExists(taskInstance, Instant.now())
                    log.debug(
                        "Rescheduled task for next command: groupId={}, pendingCount={}",
                        groupId,
                        pendingCount,
                    )
                } else {
                    log.debug("No more pending commands, task ending: groupId={}", groupId)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <P : Any> parsePayload(
        payloadJson: String?,
        payloadType: KClass<P>,
    ): P {
        if (payloadJson == null) {
            return null as P
        }
        return objectMapper.readValue(payloadJson, payloadType.java)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMetadata(metadataJson: String?): Map<String, Any?> {
        if (metadataJson == null) return emptyMap()
        return objectMapper.readValue(metadataJson, Map::class.java) as Map<String, Any?>
    }
}

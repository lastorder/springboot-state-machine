package com.example.statemachine.commandinbox.scheduler

import com.example.statemachine.commandinbox.domain.CommandInbox
import com.example.statemachine.commandinbox.repository.CommandInboxRepository
import com.example.statemachine.commandinbox.service.CommandInboxService
import com.example.statemachine.statemachine.service.StateMachineService
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

@Configuration
class OrderStateMachineTaskConfig(
    private val commandInboxService: CommandInboxService,
    private val commandInboxRepository: CommandInboxRepository,
    private val stateMachineService: StateMachineService,
    private val objectMapper: ObjectMapper,
    private val schedulerClient: SchedulerClient,
) {
    private val log = LoggerFactory.getLogger(OrderStateMachineTaskConfig::class.java)

    companion object {
        const val TASK_NAME = "order-state-machine"
        const val NEXT_COMMAND_DELAY_MS = 100L
    }

    @Bean
    fun orderStateMachineTask(): OneTimeTask<OrderTaskData> {
        return object : OneTimeTask<OrderTaskData>(
            TASK_NAME,
            OrderTaskData::class.java,
            FailureHandler.OnFailureRetryLater(Duration.ofMinutes(1)),
        ) {
            override fun executeOnce(
                taskInstance: TaskInstance<OrderTaskData>,
                executionContext: ExecutionContext,
            ) {
                val orderId = taskInstance.data.orderId
                log.debug("Processing order task: orderId={}", orderId)

                val command = commandInboxRepository.findNextPendingCommand(orderId)

                if (command == null) {
                    log.debug("No pending commands, task ending: orderId={}", orderId)
                    return
                }

                processCommand(command, orderId)

                scheduleSelfIfHasPendingCommands(orderId)
            }

            private fun processCommand(
                command: CommandInbox,
                orderId: Long,
            ) {
                val commandId = command.id!!
                log.info(
                    "Processing command: commandId={}, orderId={}, event={}",
                    commandId,
                    orderId,
                    command.eventType,
                )

                try {
                    val expiresAt = command.expiresAt
                    if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
                        commandInboxService.markExpired(commandId)
                        log.warn("Command expired: commandId={}", commandId)
                        return
                    }

                    val headers: Map<String, Any> =
                        command.headers?.let {
                            @Suppress("UNCHECKED_CAST")
                            objectMapper.readValue(it, Map::class.java) as Map<String, Any>
                        } ?: emptyMap()

                    val success = stateMachineService.sendEvent(orderId, command.eventType, headers)

                    if (success) {
                        commandInboxService.markCompleted(commandId)
                        log.info("Command completed: commandId={}, orderId={}", commandId, orderId)
                    } else {
                        commandInboxService.markSkipped(
                            commandId,
                            "State machine rejected event: ${command.eventType}",
                        )
                        log.warn(
                            "Command skipped (state machine rejected): commandId={}, orderId={}, event={}",
                            commandId,
                            orderId,
                            command.eventType,
                        )
                    }
                } catch (e: Exception) {
                    commandInboxService.markSkipped(commandId, e.message ?: "Unknown error")
                    log.warn(
                        "Command skipped (exception): commandId={}, orderId={}, error={}",
                        commandId,
                        orderId,
                        e.message,
                    )
                }
            }

            private fun scheduleSelfIfHasPendingCommands(orderId: Long) {
                Thread.sleep(NEXT_COMMAND_DELAY_MS)

                val pendingCount = commandInboxRepository.countPendingCommands(orderId)

                if (pendingCount > 0) {
                    val taskInstance =
                        TaskInstance(TASK_NAME, orderId.toString(), OrderTaskData(orderId))
                    schedulerClient.scheduleIfNotExists(taskInstance, Instant.now())
                    log.debug(
                        "Rescheduled task for next command: orderId={}, pendingCount={}",
                        orderId,
                        pendingCount,
                    )
                } else {
                    log.debug("No more pending commands, task ending: orderId={}", orderId)
                }
            }
        }
    }
}

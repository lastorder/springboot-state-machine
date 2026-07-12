package com.example.statemachine.commandinbox.scheduler

import com.example.statemachine.commandinbox.domain.CommandInbox
import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.domain.CommandSource
import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.commandinbox.repository.CommandInboxRepository
import com.example.statemachine.commandinbox.service.CommandInboxService
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.repository.OrderRepository
import com.github.kagkarlsson.scheduler.task.ExecutionContext
import com.github.kagkarlsson.scheduler.task.TaskInstance
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask
import com.github.kagkarlsson.scheduler.task.helper.Tasks
import com.github.kagkarlsson.scheduler.task.schedule.Schedules
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant

@Configuration
class ValidationTimeoutTaskConfig(
    private val orderRepository: OrderRepository,
    private val commandInboxService: CommandInboxService,
    private val commandInboxRepository: CommandInboxRepository,
    @Value("\${order.validation.timeout-minutes:10}") private val timeoutMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(ValidationTimeoutTaskConfig::class.java)

    companion object {
        const val VALIDATION_TIMEOUT_TASK_NAME = "validation-timeout-check"
    }

    @Bean
    fun validationTimeoutTask(): OneTimeTask<TimeoutCheckData> =
        object : OneTimeTask<TimeoutCheckData>(
            VALIDATION_TIMEOUT_TASK_NAME,
            TimeoutCheckData::class.java,
        ) {
            override fun executeOnce(
                taskInstance: TaskInstance<TimeoutCheckData>,
                executionContext: ExecutionContext,
            ) {
                val data = taskInstance.data

                val order = orderRepository.findById(data.orderId)
                if (order != null && order.status == OrderStatus.PENDING_VALIDATION) {
                    log.warn("Validation timeout: orderId={}", data.orderId)

                    commandInboxService.submitCommand(
                        orderId = data.orderId,
                        event = OrderEvent.VALIDATION_TIMEOUT,
                        source = CommandSource.SCHEDULED,
                        priority = CommandPriority.HIGH,
                    )
                } else {
                    log.debug("Validation already completed, skipping timeout: orderId={}", data.orderId)
                }
            }
        }

    @Bean
    fun commandCleanupTask(): com.github.kagkarlsson.scheduler.task.helper.RecurringTask<Void> =
        Tasks
            .recurring("command-cleanup", Schedules.fixedDelay(java.time.Duration.ofHours(1)))
            .execute { _: TaskInstance<Void>, _: ExecutionContext ->
                log.info("Running command cleanup task")

                val expiredCommands = commandInboxRepository.findExpiredCommands(Instant.now())
                expiredCommands.forEach { command ->
                    commandInboxRepository.save(
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
                        ),
                    )
                }

                val cutoffDate = Instant.now().minusSeconds(30 * 24 * 60 * 60L)
                val deletedCount =
                    commandInboxRepository.deleteByStatusAndProcessedAtBefore(
                        CommandStatus.COMPLETED,
                        cutoffDate,
                    )

                log.info("Cleanup completed: expired={}, deleted={}", expiredCommands.size, deletedCount)
            }
}

package com.example.statemachine.commandinbox.scheduler

import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.commandinbox.dto.CommandMetadata
import com.example.statemachine.commandinbox.repository.CommandRepository
import com.example.statemachine.commandinbox.service.CommandBus
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.order.handler.OrderStateMachineSpec
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
    private val commandBus: CommandBus,
    private val commandRepository: CommandRepository,
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

                    val metadata =
                        CommandMetadata(
                            source = "SCHEDULED",
                            sourceReference = "validation-timeout",
                        )

                    commandBus.submit(
                        groupId = data.orderId.toString(),
                        commandType = OrderStateMachineSpec.COMMAND_TYPE,
                        payload = mapOf("event" to OrderEvent.VALIDATION_TIMEOUT.name),
                        metadata = metadata,
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

                val cutoffDate = Instant.now().minusSeconds(30 * 24 * 60 * 60L)
                val deletedCount =
                    commandRepository.deleteByStatusAndProcessedAtBefore(
                        CommandStatus.COMPLETED,
                        cutoffDate,
                    )

                log.info("Cleanup completed: deleted={}", deletedCount)
            }
}

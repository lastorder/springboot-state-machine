package com.example.statemachine.commandinbox.service

import com.example.statemachine.commandinbox.scheduler.TimeoutCheckData
import com.example.statemachine.commandinbox.scheduler.ValidationTimeoutTaskConfig
import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.TaskInstance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ValidationTimeoutService(
    private val schedulerClient: SchedulerClient,
    @Value("\${order.validation.timeout-minutes:10}") private val timeoutMinutes: Long,
) {
    private val log = LoggerFactory.getLogger(ValidationTimeoutService::class.java)

    fun scheduleValidationTimeout(orderId: Long) {
        val executeAt = Instant.now().plusSeconds(timeoutMinutes * 60)

        val taskData =
            TimeoutCheckData(
                orderId = orderId,
                validationStartedAt = Instant.now().toEpochMilli(),
            )

        val taskInstance =
            TaskInstance(
                ValidationTimeoutTaskConfig.VALIDATION_TIMEOUT_TASK_NAME,
                orderId.toString(),
                taskData,
            )

        schedulerClient.scheduleIfNotExists(taskInstance, executeAt)
        log.info("Scheduled validation timeout: orderId={}, executeAt={}", orderId, executeAt)
    }

    fun cancelValidationTimeout(orderId: Long) {
        try {
            schedulerClient.cancel(
                TaskInstance<TimeoutCheckData>(
                    ValidationTimeoutTaskConfig.VALIDATION_TIMEOUT_TASK_NAME,
                    orderId.toString(),
                ),
            )
            log.info("Cancelled validation timeout: orderId={}", orderId)
        } catch (e: Exception) {
            log.debug("No timeout to cancel for orderId={}", orderId)
        }
    }
}

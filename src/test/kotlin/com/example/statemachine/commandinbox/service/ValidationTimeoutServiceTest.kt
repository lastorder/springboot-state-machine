package com.example.statemachine.commandinbox.service

import com.github.kagkarlsson.scheduler.SchedulerClient
import com.github.kagkarlsson.scheduler.task.TaskInstance
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class ValidationTimeoutServiceTest {
    private lateinit var schedulerClient: SchedulerClient
    private lateinit var validationTimeoutService: ValidationTimeoutService

    @BeforeEach
    fun setUp() {
        schedulerClient = mockk(relaxed = true)
        validationTimeoutService = ValidationTimeoutService(schedulerClient, 10)
    }

    @Test
    @DisplayName("Should schedule validation timeout")
    fun testScheduleValidationTimeout() {
        val orderId = 1L

        validationTimeoutService.scheduleValidationTimeout(orderId)

        verify { schedulerClient.scheduleIfNotExists(any<TaskInstance<*>>(), any<Instant>()) }
    }

    @Test
    @DisplayName("Should cancel validation timeout")
    fun testCancelValidationTimeout() {
        val orderId = 1L

        validationTimeoutService.cancelValidationTimeout(orderId)

        verify { schedulerClient.cancel(any<TaskInstance<*>>()) }
    }

    @Test
    @DisplayName("Should handle exception when cancelling timeout")
    fun testCancelValidationTimeout_WithException() {
        val orderId = 1L

        every { schedulerClient.cancel(any<TaskInstance<*>>()) } throws RuntimeException("Not found")

        validationTimeoutService.cancelValidationTimeout(orderId)
    }

    @Test
    @DisplayName("Should schedule with custom timeout minutes")
    fun testScheduleValidationTimeout_CustomTimeout() {
        val customTimeoutService = ValidationTimeoutService(schedulerClient, 30)
        val orderId = 1L

        customTimeoutService.scheduleValidationTimeout(orderId)

        verify { schedulerClient.scheduleIfNotExists(any<TaskInstance<*>>(), any<Instant>()) }
    }
}

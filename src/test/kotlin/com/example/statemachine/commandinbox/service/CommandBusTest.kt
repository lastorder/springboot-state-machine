package com.example.statemachine.commandinbox.service

import com.example.statemachine.commandinbox.domain.BackoffStrategy
import com.example.statemachine.commandinbox.domain.Command
import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.commandinbox.dto.BackoffConfig
import com.example.statemachine.commandinbox.dto.CommandMetadata
import com.example.statemachine.commandinbox.exception.DuplicateCommandException
import com.example.statemachine.commandinbox.repository.CommandRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kagkarlsson.scheduler.SchedulerClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CommandBusTest {
    private lateinit var commandRepository: CommandRepository
    private lateinit var schedulerClient: SchedulerClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var commandBus: CommandBus

    @BeforeEach
    fun setUp() {
        commandRepository = mockk()
        schedulerClient = mockk(relaxed = true)
        objectMapper = ObjectMapper()
        commandBus = CommandBus(commandRepository, schedulerClient, objectMapper)
    }

    @Test
    @DisplayName("Should submit command successfully")
    fun testSubmitCommand() {
        val groupId = "order-1"
        val commandType = "TEST_COMMAND"

        every { commandRepository.existsByIdempotencyKey(groupId, commandType, any()) } returns false
        every { commandRepository.save(any()) } returns
            Command(
                id = 1L,
                groupId = groupId,
                commandType = commandType,
                idempotencyKey = "test-key",
                priority = CommandPriority.NORMAL.value,
                status = CommandStatus.PENDING,
            )

        val result =
            commandBus.submit<String>(
                groupId = groupId,
                commandType = commandType,
            )

        assertNotNull(result)
        assertEquals(groupId, result.groupId)
        assertEquals(CommandStatus.PENDING, result.status)
    }

    @Test
    @DisplayName("Should submit command with payload and metadata")
    fun testSubmitCommand_WithPayloadAndMetadata() {
        val groupId = "order-1"
        val commandType = "TEST_COMMAND"
        val payload = mapOf("amount" to 100.0)
        val metadata = CommandMetadata(traceId = "trace-123", source = "HTTP")

        every { commandRepository.existsByIdempotencyKey(groupId, commandType, any()) } returns false
        every { commandRepository.save(any()) } returns
            Command(
                id = 1L,
                groupId = groupId,
                commandType = commandType,
                idempotencyKey = "test-key",
                priority = CommandPriority.NORMAL.value,
                status = CommandStatus.PENDING,
            )

        val result =
            commandBus.submit<Map<String, Double>>(
                groupId = groupId,
                commandType = commandType,
                payload = payload,
                metadata = metadata,
            )

        assertNotNull(result)
        assertEquals(groupId, result.groupId)
    }

    @Test
    @DisplayName("Should throw exception for duplicate command")
    fun testSubmitCommand_DuplicateCommand() {
        val groupId = "order-1"
        val commandType = "TEST_COMMAND"
        val idempotencyKey = "unique-key"

        every { commandRepository.existsByIdempotencyKey(groupId, commandType, idempotencyKey) } returns true

        assertThrows(DuplicateCommandException::class.java) {
            commandBus.submit<String>(
                groupId = groupId,
                commandType = commandType,
                idempotencyKey = idempotencyKey,
            )
        }
    }

    @Test
    @DisplayName("Should submit command with high priority")
    fun testSubmitCommand_HighPriority() {
        val groupId = "order-1"
        val commandType = "TEST_COMMAND"
        val priority = CommandPriority.HIGH

        every { commandRepository.existsByIdempotencyKey(groupId, commandType, any()) } returns false
        every { commandRepository.save(any()) } returns
            Command(
                id = 1L,
                groupId = groupId,
                commandType = commandType,
                idempotencyKey = "test-key",
                priority = priority.value,
                status = CommandStatus.PENDING,
            )

        val result =
            commandBus.submit<String>(
                groupId = groupId,
                commandType = commandType,
                priority = priority,
            )

        assertNotNull(result)
        assertEquals(CommandStatus.PENDING, result.status)
    }

    @Test
    @DisplayName("Should submit command with backoff strategy")
    fun testSubmitCommand_WithBackoffStrategy() {
        val groupId = "order-1"
        val commandType = "TEST_COMMAND"
        val backoffStrategy = BackoffStrategy.EXPONENTIAL
        val backoffConfig = BackoffConfig(initialDelayMs = 2000, maxDelayMs = 60000, multiplier = 2.0)

        every { commandRepository.existsByIdempotencyKey(groupId, commandType, any()) } returns false
        every { commandRepository.save(any()) } returns
            Command(
                id = 1L,
                groupId = groupId,
                commandType = commandType,
                idempotencyKey = "test-key",
                priority = CommandPriority.NORMAL.value,
                backoffStrategy = backoffStrategy,
                backoffConfig = objectMapper.writeValueAsString(backoffConfig),
                status = CommandStatus.PENDING,
            )

        val result =
            commandBus.submit<String>(
                groupId = groupId,
                commandType = commandType,
                backoffStrategy = backoffStrategy,
                backoffConfig = backoffConfig,
            )

        assertNotNull(result)
        assertEquals(CommandStatus.PENDING, result.status)
    }

    @Test
    @DisplayName("Should mark command as completed")
    fun testMarkCompleted() {
        val commandId = 1L

        every { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) } returns 1

        commandBus.markCompleted(commandId)

        verify { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("Should mark command as completed with response")
    fun testMarkCompleted_WithResponse() {
        val commandId = 1L
        val response = mapOf("result" to "success")

        every { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) } returns 1

        commandBus.markCompleted(commandId, response)

        verify { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("Should mark command as skipped")
    fun testMarkSkipped() {
        val commandId = 1L
        val reason = "State machine rejected"

        every { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) } returns 1

        commandBus.markSkipped(commandId, reason)

        verify { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("Should mark command as failed")
    fun testMarkFailed() {
        val commandId = 1L
        val error = "Processing failed"

        every { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) } returns 1

        commandBus.markFailed(commandId, error)

        verify { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("Should mark command as expired")
    fun testMarkExpired() {
        val commandId = 1L

        every { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) } returns 1

        commandBus.markExpired(commandId)

        verify { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("Should schedule retry for command")
    fun testScheduleRetry() {
        val command =
            Command(
                id = 1L,
                groupId = "order-1",
                commandType = "TEST_COMMAND",
                idempotencyKey = "test-key",
                maxRetries = 3,
                retryCount = 0,
                backoffStrategy = BackoffStrategy.FIXED,
                status = CommandStatus.PROCESSING,
            )

        every { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) } returns 1

        commandBus.scheduleRetry(command, "Temporary error")

        verify { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("Should mark failed when max retries exceeded")
    fun testScheduleRetry_MaxRetriesExceeded() {
        val command =
            Command(
                id = 1L,
                groupId = "order-1",
                commandType = "TEST_COMMAND",
                idempotencyKey = "test-key",
                maxRetries = 3,
                retryCount = 3,
                backoffStrategy = BackoffStrategy.FIXED,
                status = CommandStatus.PROCESSING,
            )

        every { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) } returns 1

        commandBus.scheduleRetry(command, "Final error")

        verify { commandRepository.updateStatus(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("Should get command status")
    fun testGetCommandStatus() {
        val groupId = "order-1"
        val commandId = 1L
        val command =
            Command(
                id = commandId,
                groupId = groupId,
                commandType = "TEST_COMMAND",
                idempotencyKey = "test-key",
                status = CommandStatus.COMPLETED,
            )

        every { commandRepository.findByIdAndGroupId(commandId, groupId) } returns command

        val result = commandBus.getCommandStatus(groupId, commandId)

        assertNotNull(result)
        assertEquals(commandId, result!!.id)
        assertEquals(CommandStatus.COMPLETED, result.status)
    }

    @Test
    @DisplayName("Should return null for non-existent command status")
    fun testGetCommandStatus_NotFound() {
        val groupId = "order-1"
        val commandId = 999L

        every { commandRepository.findByIdAndGroupId(commandId, groupId) } returns null

        val result = commandBus.getCommandStatus(groupId, commandId)

        assertNull(result)
    }
}

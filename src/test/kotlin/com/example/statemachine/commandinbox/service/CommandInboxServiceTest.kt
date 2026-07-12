package com.example.statemachine.commandinbox.service

import com.example.statemachine.commandinbox.domain.CommandInbox
import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.commandinbox.domain.CommandSource
import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.commandinbox.exception.DuplicateCommandException
import com.example.statemachine.commandinbox.exception.ExpiredCommandException
import com.example.statemachine.commandinbox.repository.CommandInboxRepository
import com.example.statemachine.domain.enums.OrderEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kagkarlsson.scheduler.SchedulerClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

class CommandInboxServiceTest {
    private lateinit var commandInboxRepository: CommandInboxRepository
    private lateinit var schedulerClient: SchedulerClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var commandInboxService: CommandInboxService

    @BeforeEach
    fun setUp() {
        commandInboxRepository = mockk()
        schedulerClient = mockk(relaxed = true)
        objectMapper = ObjectMapper()
        commandInboxService = CommandInboxService(commandInboxRepository, schedulerClient, objectMapper)
    }

    @Test
    @DisplayName("Should submit command successfully")
    fun testSubmitCommand() {
        val orderId = 1L
        val event = OrderEvent.USER_CONFIRM
        val source = CommandSource.HTTP

        every { commandInboxRepository.existsByIdempotencyKey(orderId, event, any()) } returns false
        every { commandInboxRepository.save(any()) } returns
            CommandInbox(
                id = 1L,
                orderId = orderId,
                eventType = event,
                source = source,
                idempotencyKey = "test-key",
                priority = CommandPriority.NORMAL.value,
                status = CommandStatus.PENDING,
            )

        val result = commandInboxService.submitCommand(orderId, event, source)

        assertNotNull(result)
        assertEquals(orderId, result.orderId)
        assertEquals(CommandStatus.PENDING, result.status)
    }

    @Test
    @DisplayName("Should submit command with payload and headers")
    fun testSubmitCommand_WithPayloadAndHeaders() {
        val orderId = 1L
        val event = OrderEvent.PAY
        val source = CommandSource.HTTP
        val payload = mapOf("amount" to 100.0)
        val headers = mapOf("source" to "web")

        every { commandInboxRepository.existsByIdempotencyKey(orderId, event, any()) } returns false
        every { commandInboxRepository.save(any()) } returns
            CommandInbox(
                id = 1L,
                orderId = orderId,
                eventType = event,
                source = source,
                idempotencyKey = "test-key",
                priority = CommandPriority.NORMAL.value,
                status = CommandStatus.PENDING,
            )

        val result = commandInboxService.submitCommand(
            orderId = orderId,
            event = event,
            source = source,
            payload = payload,
            headers = headers,
        )

        assertNotNull(result)
        assertEquals(orderId, result.orderId)
    }

    @Test
    @DisplayName("Should throw exception for expired command")
    fun testSubmitCommand_ExpiredCommand() {
        val orderId = 1L
        val event = OrderEvent.USER_CONFIRM
        val source = CommandSource.HTTP
        val expiresAt = Instant.now().minusSeconds(3600)

        assertThrows(ExpiredCommandException::class.java) {
            commandInboxService.submitCommand(
                orderId = orderId,
                event = event,
                source = source,
                expiresAt = expiresAt,
            )
        }
    }

    @Test
    @DisplayName("Should throw exception for duplicate command")
    fun testSubmitCommand_DuplicateCommand() {
        val orderId = 1L
        val event = OrderEvent.USER_CONFIRM
        val source = CommandSource.HTTP
        val idempotencyKey = "unique-key"

        every { commandInboxRepository.existsByIdempotencyKey(orderId, event, idempotencyKey) } returns true

        assertThrows(DuplicateCommandException::class.java) {
            commandInboxService.submitCommand(
                orderId = orderId,
                event = event,
                source = source,
                idempotencyKey = idempotencyKey,
            )
        }
    }

    @Test
    @DisplayName("Should submit command with high priority")
    fun testSubmitCommand_HighPriority() {
        val orderId = 1L
        val event = OrderEvent.INVENTORY_SUCCESS
        val source = CommandSource.KAFKA
        val priority = CommandPriority.HIGH

        every { commandInboxRepository.existsByIdempotencyKey(orderId, event, any()) } returns false
        every { commandInboxRepository.save(any()) } returns
            CommandInbox(
                id = 1L,
                orderId = orderId,
                eventType = event,
                source = source,
                idempotencyKey = "test-key",
                priority = priority.value,
                status = CommandStatus.PENDING,
            )

        val result = commandInboxService.submitCommand(
            orderId = orderId,
            event = event,
            source = source,
            priority = priority,
        )

        assertNotNull(result)
        assertEquals(CommandStatus.PENDING, result.status)
    }

    @Test
    @DisplayName("Should submit command with urgent priority")
    fun testSubmitCommand_UrgentPriority() {
        val orderId = 1L
        val event = OrderEvent.CANCEL
        val source = CommandSource.HTTP
        val priority = CommandPriority.URGENT

        every { commandInboxRepository.existsByIdempotencyKey(orderId, event, any()) } returns false
        every { commandInboxRepository.save(any()) } returns
            CommandInbox(
                id = 1L,
                orderId = orderId,
                eventType = event,
                source = source,
                idempotencyKey = "test-key",
                priority = priority.value,
                status = CommandStatus.PENDING,
            )

        val result = commandInboxService.submitCommand(
            orderId = orderId,
            event = event,
            source = source,
            priority = priority,
        )

        assertNotNull(result)
        assertEquals(CommandStatus.PENDING, result.status)
    }

    @Test
    @DisplayName("Should mark command as completed")
    fun testMarkCompleted() {
        val commandId = 1L
        val existingCommand = CommandInbox(
            id = commandId,
            orderId = 1L,
            eventType = OrderEvent.USER_CONFIRM,
            source = CommandSource.HTTP,
            idempotencyKey = "test-key",
            priority = CommandPriority.NORMAL.value,
            status = CommandStatus.PENDING,
        )

        every { commandInboxRepository.findById(commandId) } returns existingCommand
        every { commandInboxRepository.save(any()) } returns existingCommand

        commandInboxService.markCompleted(commandId)

        verify { commandInboxRepository.save(any()) }
    }

    @Test
    @DisplayName("Should throw exception when marking non-existent command as completed")
    fun testMarkCompleted_NotFound() {
        val commandId = 999L

        every { commandInboxRepository.findById(commandId) } returns null

        assertThrows(IllegalArgumentException::class.java) {
            commandInboxService.markCompleted(commandId)
        }
    }

    @Test
    @DisplayName("Should mark command as skipped")
    fun testMarkSkipped() {
        val commandId = 1L
        val reason = "State machine rejected"
        val existingCommand = CommandInbox(
            id = commandId,
            orderId = 1L,
            eventType = OrderEvent.USER_CONFIRM,
            source = CommandSource.HTTP,
            idempotencyKey = "test-key",
            priority = CommandPriority.NORMAL.value,
            status = CommandStatus.PENDING,
        )

        every { commandInboxRepository.findById(commandId) } returns existingCommand
        every { commandInboxRepository.save(any()) } returns existingCommand

        commandInboxService.markSkipped(commandId, reason)

        verify { commandInboxRepository.save(any()) }
    }

    @Test
    @DisplayName("Should mark command as expired")
    fun testMarkExpired() {
        val commandId = 1L
        val existingCommand = CommandInbox(
            id = commandId,
            orderId = 1L,
            eventType = OrderEvent.USER_CONFIRM,
            source = CommandSource.HTTP,
            idempotencyKey = "test-key",
            priority = CommandPriority.NORMAL.value,
            status = CommandStatus.PENDING,
            expiresAt = Instant.now().minusSeconds(60),
        )

        every { commandInboxRepository.findById(commandId) } returns existingCommand
        every { commandInboxRepository.save(any()) } returns existingCommand

        commandInboxService.markExpired(commandId)

        verify { commandInboxRepository.save(any()) }
    }

    @Test
    @DisplayName("Should get command status")
    fun testGetCommandStatus() {
        val orderId = 1L
        val commandId = 1L
        val command = CommandInbox(
            id = commandId,
            orderId = orderId,
            eventType = OrderEvent.USER_CONFIRM,
            source = CommandSource.HTTP,
            idempotencyKey = "test-key",
            priority = CommandPriority.NORMAL.value,
            status = CommandStatus.COMPLETED,
        )

        every { commandInboxRepository.findByIdAndOrderId(commandId, orderId) } returns command

        val result = commandInboxService.getCommandStatus(orderId, commandId)

        assertNotNull(result)
        assertEquals(commandId, result!!.id)
        assertEquals(CommandStatus.COMPLETED, result.status)
    }

    @Test
    @DisplayName("Should return null for non-existent command status")
    fun testGetCommandStatus_NotFound() {
        val orderId = 1L
        val commandId = 999L

        every { commandInboxRepository.findByIdAndOrderId(commandId, orderId) } returns null

        val result = commandInboxService.getCommandStatus(orderId, commandId)

        assertEquals(null, result)
    }

    @Test
    @DisplayName("Should submit command with correlation id and source reference")
    fun testSubmitCommand_WithCorrelationId() {
        val orderId = 1L
        val event = OrderEvent.PAY
        val source = CommandSource.HTTP
        val correlationId = "correlation-123"
        val sourceReference = "ref-456"

        every { commandInboxRepository.existsByIdempotencyKey(orderId, event, any()) } returns false
        every { commandInboxRepository.save(any()) } returns
            CommandInbox(
                id = 1L,
                orderId = orderId,
                eventType = event,
                source = source,
                sourceReference = sourceReference,
                correlationId = correlationId,
                idempotencyKey = "test-key",
                priority = CommandPriority.NORMAL.value,
                status = CommandStatus.PENDING,
            )

        val result = commandInboxService.submitCommand(
            orderId = orderId,
            event = event,
            source = source,
            correlationId = correlationId,
            sourceReference = sourceReference,
        )

        assertNotNull(result)
        assertEquals(orderId, result.orderId)
    }
}

package com.example.statemachine.commandinbox.domain

import com.example.statemachine.domain.enums.OrderEvent
import java.time.Instant

class CommandInbox(
    val id: Long? = null,
    val orderId: Long,
    val eventType: OrderEvent,
    val source: CommandSource,
    val sourceReference: String? = null,
    val correlationId: String? = null,
    val payload: String? = null,
    val headers: String? = null,
    val idempotencyKey: String? = null,
    var priority: Int = CommandPriority.NORMAL.value,
    var expiresAt: Instant? = null,
    var status: CommandStatus = CommandStatus.PENDING,
    var errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var processedAt: Instant? = null,
)

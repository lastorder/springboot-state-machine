package com.example.statemachine.commandinbox.domain

import java.time.Instant

class Command(
    val id: Long? = null,
    val groupId: String,
    val commandType: String,
    val idempotencyKey: String,
    val payload: String? = null,
    var response: String? = null,
    val metadata: String? = null,
    val priority: Int = 0,
    val maxRetries: Int = 3,
    var retryCount: Int = 0,
    val backoffStrategy: BackoffStrategy = BackoffStrategy.FIXED,
    val backoffConfig: String? = null,
    var status: CommandStatus = CommandStatus.PENDING,
    var errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var processedAt: Instant? = null,
    var nextRetryAt: Instant? = null,
)

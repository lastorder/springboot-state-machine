package com.example.statemachine.task.spec

import java.io.Serializable
import java.time.Instant

data class TaskContext<P : Serializable>(
    val instanceId: String,
    val payload: P,
    val scheduledTime: Instant,
    val executionTime: Instant = Instant.now(),
    val retryCount: Int = 0,
    val metadata: Map<String, String> = emptyMap(),
)

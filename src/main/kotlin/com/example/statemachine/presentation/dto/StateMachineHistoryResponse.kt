package com.example.statemachine.presentation.dto

import java.time.Instant

data class StateMachineHistoryResponse(
    val id: Long,
    val machineId: String,
    val fromState: String?,
    val toState: String,
    val event: String?,
    val headers: Map<String, Any?>?,
    val createdAt: Instant,
)

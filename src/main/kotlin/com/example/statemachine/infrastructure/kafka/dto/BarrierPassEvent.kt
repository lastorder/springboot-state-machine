package com.example.statemachine.infrastructure.kafka.dto

data class BarrierPassEvent(
    val orderNo: String,
    val barrierType: String,
    val flowType: String = "PR_ACCEPT",
    val success: Boolean = true,
)

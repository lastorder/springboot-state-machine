package com.example.statemachine.infrastructure.kafka.dto

data class VomEvent(
    val orderNo: String,
    @Deprecated("Use orderNo instead", ReplaceWith("orderNo"))
    val orderId: Long? = null,
)

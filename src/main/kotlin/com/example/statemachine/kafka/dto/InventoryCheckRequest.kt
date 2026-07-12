package com.example.statemachine.kafka.dto

import java.time.Instant

data class InventoryCheckRequest(
    val orderId: Long,
    val product: String,
    val quantity: Int,
    val timestamp: Instant = Instant.now(),
)

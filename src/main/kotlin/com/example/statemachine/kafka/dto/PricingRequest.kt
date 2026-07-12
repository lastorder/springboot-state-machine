package com.example.statemachine.kafka.dto

import java.time.Instant

data class PricingRequest(
    val orderId: Long,
    val product: String,
    val quantity: Int,
    val inventoryReference: String,
    val timestamp: Instant = Instant.now(),
)

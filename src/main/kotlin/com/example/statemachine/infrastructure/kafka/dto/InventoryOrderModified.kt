package com.example.statemachine.infrastructure.kafka.dto

import java.time.Instant

data class InventoryOrderModified(
    val orderId: Long,
    val modifiedProduct: String? = null,
    val modifiedQuantity: Int? = null,
    val reason: String,
    val timestamp: Instant = Instant.now(),
)

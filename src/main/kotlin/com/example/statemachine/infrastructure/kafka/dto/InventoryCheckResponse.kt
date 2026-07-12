package com.example.statemachine.infrastructure.kafka.dto

import java.time.Instant

data class InventoryCheckResponse(
    val orderId: Long,
    val available: Boolean,
    val inventoryReference: String? = null,
    val modifiedProduct: String? = null,
    val modifiedQuantity: Int? = null,
    val message: String? = null,
    val timestamp: Instant = Instant.now(),
)

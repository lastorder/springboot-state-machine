package com.example.statemachine.infrastructure.kafka.dto

import java.math.BigDecimal
import java.time.Instant

data class PricingResponse(
    val orderId: Long,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal,
    val pricingReference: String,
    val validUntil: Instant? = null,
    val timestamp: Instant = Instant.now(),
)

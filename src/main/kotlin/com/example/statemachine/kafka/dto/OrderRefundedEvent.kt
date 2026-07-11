package com.example.statemachine.kafka.dto

import java.math.BigDecimal
import java.time.Instant

data class OrderRefundedEvent(
    val orderId: Long,
    val refundAmount: BigDecimal,
    val reason: String,
    val refundedAt: Instant,
)

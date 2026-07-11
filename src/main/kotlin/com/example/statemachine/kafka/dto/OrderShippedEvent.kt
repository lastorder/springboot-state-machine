package com.example.statemachine.kafka.dto

import java.time.Instant

data class OrderShippedEvent(
    val orderId: Long,
    val trackingNumber: String,
    val shippedAt: Instant,
)

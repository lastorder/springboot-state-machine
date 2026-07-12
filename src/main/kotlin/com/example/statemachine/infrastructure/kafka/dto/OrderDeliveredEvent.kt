package com.example.statemachine.infrastructure.kafka.dto

import java.time.Instant

data class OrderDeliveredEvent(
    val orderId: Long,
    val deliveredAt: Instant,
)

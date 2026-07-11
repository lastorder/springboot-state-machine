package com.example.statemachine.kafka.dto

import java.time.Instant

data class OrderDeliveredEvent(
    val orderId: Long,
    val deliveredAt: Instant,
)

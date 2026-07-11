package com.example.statemachine.kafka.dto

import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import java.time.Instant

data class OrderStatusChangeEvent(
    val orderId: Long,
    val fromStatus: OrderStatus?,
    val toStatus: OrderStatus?,
    val event: OrderEvent?,
    val timestamp: Instant,
)

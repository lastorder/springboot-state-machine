package com.example.statemachine.infrastructure.kafka.dto

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import java.time.Instant

data class OrderStatusChangeEvent(
    val orderId: Long,
    val fromStatus: OrderStatus?,
    val toStatus: OrderStatus?,
    val event: OrderEvent?,
    val timestamp: Instant,
)

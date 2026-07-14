package com.example.statemachine.presentation.dto

import com.example.statemachine.domain.enums.OrderStatus
import java.math.BigDecimal
import java.time.Instant

data class OrderResponse(
    val id: Long,
    val orderNo: String,
    val productId: String?,
    val productName: String?,
    val quantity: Int,
    val amount: BigDecimal?,
    val status: OrderStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

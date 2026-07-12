package com.example.statemachine.controller.dto

import com.example.statemachine.domain.OrderStatus
import java.math.BigDecimal
import java.time.Instant

data class OrderResponse(
    val id: Long,
    val product: String,
    val quantity: Int,
    val amount: BigDecimal,
    val status: OrderStatus,
    val inventoryStatus: String?,
    val inventoryReference: String?,
    val pricingReference: String?,
    val unitPrice: BigDecimal?,
    val confirmedPrice: BigDecimal?,
    val modificationReason: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

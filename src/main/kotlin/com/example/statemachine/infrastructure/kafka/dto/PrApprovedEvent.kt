package com.example.statemachine.infrastructure.kafka.dto

import com.example.statemachine.domain.enums.Market

data class PrApprovedEvent(
    val orderId: Long,
    val orderNo: String,
    val productId: String? = null,
    val productName: String? = null,
    val quantity: Int? = null,
    val amount: Double? = null,
    val market: Market,
)

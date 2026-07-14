package com.example.statemachine.infrastructure.kafka.dto

import java.math.BigDecimal

data class PrApprovedEvent(
    val orderId: Long,
    val orderNo: String,
    val productId: String? = null,
    val productName: String? = null,
    val quantity: Int? = null,
    val amount: BigDecimal? = null,
)

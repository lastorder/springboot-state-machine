package com.example.statemachine.kafka.dto

import java.math.BigDecimal
import java.time.Instant

data class PaymentConfirmedEvent(
    val orderId: Long,
    val transactionId: String,
    val amount: BigDecimal,
    val confirmedAt: Instant,
)

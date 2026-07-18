package com.example.statemachine.infrastructure.kafka.dto

import com.example.statemachine.domain.enums.Market

data class ChangeTriggerEvent(
    val orderNo: String,
    val market: Market,
    val eventType: String = "PURCHASE_REQUEST_ACCEPT",
)

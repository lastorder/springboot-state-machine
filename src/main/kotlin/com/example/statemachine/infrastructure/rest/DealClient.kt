package com.example.statemachine.infrastructure.rest

interface DealClient {
    fun syncOrder(orderId: Long)
}

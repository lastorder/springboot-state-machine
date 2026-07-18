package com.example.statemachine.infrastructure.rest

interface DealClient {
    fun syncOrderByOrderNo(orderNo: String)
}

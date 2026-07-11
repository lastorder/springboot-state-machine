package com.example.statemachine.domain

enum class OrderStatus {
    CREATED,
    PENDING_PAYMENT,
    PAID,
    PENDING_SHIPMENT,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED,
}

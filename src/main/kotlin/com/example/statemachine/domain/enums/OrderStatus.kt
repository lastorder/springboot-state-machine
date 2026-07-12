package com.example.statemachine.domain.enums

enum class OrderStatus {
    CREATED,

    PENDING_VALIDATION,
    INVENTORY_CHECK,
    PRICING_CHECK,

    PENDING_CONFIRMATION,
    PENDING_PAYMENT,
    PAID,
    PENDING_SHIPMENT,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REJECTED,
    REFUNDED,
}

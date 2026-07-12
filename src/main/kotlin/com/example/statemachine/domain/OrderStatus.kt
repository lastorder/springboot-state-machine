package com.example.statemachine.domain

enum class OrderStatus {
    CREATED,

    // Fork/Join 并行验证状态
    PENDING_VALIDATION, // Fork 状态 - 并行验证入口
    INVENTORY_CHECK, // 子状态 - 库存检查
    PRICING_CHECK, // 子状态 - 报价检查

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

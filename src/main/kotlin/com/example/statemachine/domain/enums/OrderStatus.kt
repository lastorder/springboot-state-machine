package com.example.statemachine.domain.enums

enum class OrderStatus {
    INIT,
    LOCAL_INITIALIZED,
    FACTORY_ORDER_SUBMITTED,
    ORDER_INITIALIZE_SUCCEED,
    ORDER_INITIALIZE_FAILED,
}

package com.example.statemachine.domain.enums

enum class OrderStatus {
    INIT,
    LOCAL_INITIALIZED,
    FACTORY_ORDER_SUBMITTED,
    FIRST_VOM_RECEIVED,
    FIRST_DOM_RECEIVED,
    ORDER_INITIALIZE_SUCCEED,
    ORDER_INITIALIZE_FAILED,
}

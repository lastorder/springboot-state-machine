package com.example.statemachine.domain

enum class OrderEvent {
    SUBMIT,
    PAY,
    CONFIRM_PAYMENT,
    SHIP,
    DELIVER,
    CANCEL,
    REFUND,
}

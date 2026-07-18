package com.example.statemachine.domain.enums

enum class OrderEvent {
    PR_APPROVED,
    VOM,
    DOM,
    VOM_FAILED,
    PURCHASE_REQUEST_ACCEPT,
    PURCHASE_REQUEST_ACCEPT_SUCCESS,
    PURCHASE_REQUEST_ACCEPT_FAILED,
    CDOA_ACCEPT,
    CDOA_ACCEPT_SUCCESS,
    CDOA_ACCEPT_FAILED,
}

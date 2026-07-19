package com.example.statemachine.statemachine.action

import com.example.statemachine.api.StateContext

object OrderActionUtils {
    fun extractOrderNo(context: StateContext<com.example.statemachine.domain.enums.OrderStatus>): String =
        context.headers["orderNo"] as? String ?: context.machineId
}

package com.example.statemachine.statemachine.action

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import org.springframework.statemachine.StateContext

object OrderActionUtils {
    fun extractOrderNo(context: StateContext<OrderStatus, OrderEvent>): String? =
        context.message?.headers?.get("orderNo") as? String ?: context.stateMachine.id
}

package com.example.statemachine.application.task

import com.example.statemachine.domain.enums.OrderEvent
import java.io.Serializable

data class OrderEventPayload(
    val orderNo: String,
    val event: OrderEvent,
    val headers: Map<String, Any?> = emptyMap(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

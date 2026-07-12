package com.example.statemachine.action

import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component

@Component
class OrderModificationAction : Action<OrderStatus, OrderEvent> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus, OrderEvent>) {
        val orderId = context.message.headers["orderId", Long::class.java]
        val newProduct = context.message.headers["newProduct", String::class.java]
        val newQuantity = context.message.headers["newQuantity", Int::class.java]
        val reason = context.message.headers["modificationReason", String::class.java]

        log.info(
            "Order modification action: orderId=$orderId, newProduct=$newProduct, " +
                "newQuantity=$newQuantity, reason=$reason",
        )

        // The actual order update is handled in OrderService
        // This action is for logging and any additional processing
    }
}

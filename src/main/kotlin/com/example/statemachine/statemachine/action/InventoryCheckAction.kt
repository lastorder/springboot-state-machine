package com.example.statemachine.statemachine.action

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.enums.ValidationStatus
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.infrastructure.kafka.OrderEventProducer
import com.example.statemachine.infrastructure.kafka.dto.InventoryCheckRequest
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component

@Component
class InventoryCheckAction(
    private val orderRepository: OrderRepository,
    private val orderEventProducer: OrderEventProducer,
) : Action<OrderStatus, OrderEvent> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus, OrderEvent>) {
        val orderId = context.message.headers["orderId", Long::class.java]
        if (orderId == null) {
            log.warn("No orderId in message headers")
            return
        }

        val order = orderRepository.findById(orderId)
        if (order == null) {
            log.warn("Order not found: orderId=$orderId")
            return
        }

        if (order.inventoryCheckStatus != null && order.inventoryCheckStatus != ValidationStatus.PENDING) {
            log.info("Retrying inventory check for order: orderId=$orderId")

            val request =
                InventoryCheckRequest(
                    orderId = orderId,
                    product = order.product,
                    quantity = order.quantity,
                )
            orderEventProducer.sendInventoryCheckRequest(request)
        }
    }
}

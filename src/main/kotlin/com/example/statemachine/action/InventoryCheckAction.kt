package com.example.statemachine.action

import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.kafka.OrderEventProducer
import com.example.statemachine.kafka.dto.InventoryCheckRequest
import com.example.statemachine.repository.OrderRepository
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

        val order = orderRepository.findById(orderId).orElse(null)
        if (order == null) {
            log.warn("Order not found: orderId=$orderId")
            return
        }

        // 如果是重试，重新发送库存检查请求
        if (order.inventoryCheckStatus != null && order.inventoryCheckStatus != com.example.statemachine.domain.ValidationStatus.PENDING) {
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

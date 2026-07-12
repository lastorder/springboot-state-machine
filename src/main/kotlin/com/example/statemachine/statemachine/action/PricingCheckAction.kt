package com.example.statemachine.statemachine.action

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.enums.ValidationStatus
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.infrastructure.kafka.OrderEventProducer
import com.example.statemachine.infrastructure.kafka.dto.PricingRequest
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component

@Component
class PricingCheckAction(
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

        if (order.pricingCheckStatus != null && order.pricingCheckStatus != ValidationStatus.PENDING) {
            log.info("Retrying pricing check for order: orderId=$orderId")

            val request =
                PricingRequest(
                    orderId = orderId,
                    product = order.product,
                    quantity = order.quantity,
                    inventoryReference = order.inventoryReference ?: "PENDING-$orderId",
                )
            orderEventProducer.sendPricingRequest(request)
        }
    }
}

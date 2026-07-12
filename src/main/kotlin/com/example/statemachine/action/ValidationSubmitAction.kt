package com.example.statemachine.action

import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.kafka.OrderEventProducer
import com.example.statemachine.kafka.dto.InventoryCheckRequest
import com.example.statemachine.kafka.dto.PricingRequest
import com.example.statemachine.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component

@Component
class ValidationSubmitAction(
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

        log.info("Starting parallel validation for order: orderId=$orderId")

        // 初始化验证状态
        order.startValidation()
        orderRepository.save(order)

        // 并行发送库存检查和报价请求
        val inventoryRequest =
            InventoryCheckRequest(
                orderId = orderId,
                product = order.product,
                quantity = order.quantity,
            )

        val tempInventoryRef = "PENDING-$orderId"
        val pricingRequest =
            PricingRequest(
                orderId = orderId,
                product = order.product,
                quantity = order.quantity,
                inventoryReference = tempInventoryRef,
            )

        orderEventProducer.sendInventoryCheckRequest(inventoryRequest)
        orderEventProducer.sendPricingRequest(pricingRequest)

        log.info("Parallel validation requests sent for order: orderId=$orderId")
    }
}

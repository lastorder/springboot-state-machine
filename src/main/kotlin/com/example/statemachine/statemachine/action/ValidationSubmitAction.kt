package com.example.statemachine.statemachine.action

import com.example.statemachine.commandinbox.service.ValidationTimeoutService
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.infrastructure.kafka.OrderEventProducer
import com.example.statemachine.infrastructure.kafka.dto.InventoryCheckRequest
import com.example.statemachine.infrastructure.kafka.dto.PricingRequest
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component

@Component
class ValidationSubmitAction(
    private val orderRepository: OrderRepository,
    private val orderEventProducer: OrderEventProducer,
    private val validationTimeoutService: ValidationTimeoutService,
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

        log.info("Starting parallel validation for order: orderId=$orderId")

        order.startValidation()
        orderRepository.save(order)

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

        validationTimeoutService.scheduleValidationTimeout(orderId)

        log.info("Parallel validation requests sent for order: orderId=$orderId")
    }
}

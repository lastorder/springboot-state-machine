package com.example.statemachine.statemachine.action

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.model.Order
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.statemachine.service.StateMachineService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.action.Action
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class PrApprovedAction(
    private val orderRepository: OrderRepository,
    @Lazy private val stateMachineService: StateMachineService,
) : Action<OrderStatus, OrderEvent> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus, OrderEvent>) {
        log.info(">>> PrApprovedAction.execute() called!")

        val message = context.message
        log.debug("Message headers: ${message.headers}")

        val orderNo = message.headers.get("orderNo") as? String
        val productId = message.headers.get("productId") as? String
        val productName = message.headers.get("productName") as? String
        val quantity = (message.headers.get("quantity") as? Number)?.toInt()
        val amount =
            when (val amt = message.headers.get("amount")) {
                is BigDecimal -> amt
                is Number -> BigDecimal(amt.toString())
                is String -> BigDecimal(amt)
                else -> null
            }

        log.info("Headers: orderNo=$orderNo, productId=$productId, quantity=$quantity, amount=$amount")

        if (orderNo == null) {
            log.error("Missing required header: orderNo")
            return
        }

        log.info("Processing PR_APPROVED event: orderNo={}", orderNo)

        try {
            // Check if order already exists by orderNo
            val existingOrder = orderRepository.findByOrderNo(orderNo)
            if (existingOrder != null) {
                log.info("Order already exists: id={}, orderNo={}", existingOrder.id, existingOrder.orderNo)
                context.stateMachine.stateMachineAccessor.withRegion().resetStateMachineReactively(
                    org.springframework.statemachine.support.DefaultStateMachineContext(
                        OrderStatus.LOCAL_INITIALIZED,
                        null, null, null, null, existingOrder.id.toString()
                    )
                ).block()
                return
            }

            val order =
                Order.fromPrApproved(
                    orderNo = orderNo,
                    productId = productId,
                    productName = productName,
                    quantity = quantity,
                    amount = amount,
                )

            val savedOrder = orderRepository.save(order)
            log.info("Order saved from PR_APPROVED: id={}, orderNo={}", savedOrder.id, savedOrder.orderNo)

            // Initialize state machine with the new order ID
            stateMachineService.initializeStateMachine(savedOrder.id!!, OrderStatus.LOCAL_INITIALIZED)
        } catch (e: Exception) {
            log.error("Error in PrApprovedAction: ${e.message}", e)
        }
    }
}

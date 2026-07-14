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
        val message = context.message
        val orderId = message.headers.get("orderId", Long::class.java)
        val orderNo = message.headers.get("orderNo", String::class.java)
        val productId = message.headers.get("productId", String::class.java)
        val productName = message.headers.get("productName", String::class.java)
        val quantity = message.headers.get("quantity", Int::class.java)
        val amount = message.headers.get("amount", BigDecimal::class.java)

        if (orderId == null || orderNo == null) {
            log.error("Missing required headers: orderId={}, orderNo={}", orderId, orderNo)
            return
        }

        log.info("Processing PR_APPROVED event: orderId={}, orderNo={}", orderId, orderNo)

        val order =
            Order.fromPrApproved(
                orderNo = orderNo,
                productId = productId,
                productName = productName,
                quantity = quantity,
                amount = amount,
            )
        order.id = orderId

        val savedOrder = orderRepository.save(order)
        log.info("Order saved from PR_APPROVED: id={}, orderNo={}", savedOrder.id, savedOrder.orderNo)

        stateMachineService.initializeStateMachine(orderId, OrderStatus.LOCAL_INITIALIZED)
    }
}

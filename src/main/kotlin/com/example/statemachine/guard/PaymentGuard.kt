package com.example.statemachine.guard

import com.example.statemachine.domain.Order
import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.guard.Guard
import org.springframework.stereotype.Component

@Component
class PaymentGuard(
    private val orderRepository: OrderRepository,
) : Guard<OrderStatus, OrderEvent> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun evaluate(context: StateContext<OrderStatus, OrderEvent>): Boolean {
        val orderId = context.message.headers["orderId", Long::class.java]
        val amount = context.message.headers["amount"] as? java.math.BigDecimal

        if (orderId == null || amount == null) {
            log.warn("Payment guard rejected: missing orderId or amount")
            return false
        }

        val order = orderRepository.findById(orderId).orElse(null) as? Order
        if (order == null) {
            log.warn("Payment guard rejected: order not found, orderId=$orderId")
            return false
        }

        val valid = amount >= java.math.BigDecimal.ZERO && order.status == OrderStatus.PENDING_PAYMENT
        log.info("Payment guard evaluation: orderId=$orderId, amount=$amount, valid=$valid")
        return valid
    }
}

package com.example.statemachine.statemachine.guard

import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.statemachine.StateContext
import org.springframework.statemachine.guard.Guard
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class PaymentGuard(
    private val orderRepository: OrderRepository,
) : Guard<OrderStatus, OrderEvent> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun evaluate(context: StateContext<OrderStatus, OrderEvent>): Boolean {
        val orderId = context.message.headers["orderId", Long::class.java]
        val amount = context.message.headers["amount"] as? BigDecimal

        if (orderId == null || amount == null) {
            log.warn("Payment guard rejected: missing orderId or amount")
            return false
        }

        val order = orderRepository.findById(orderId)
        if (order == null) {
            log.warn("Payment guard rejected: order not found, orderId=$orderId")
            return false
        }

        val valid = amount >= BigDecimal.ZERO && order.status == OrderStatus.PENDING_PAYMENT
        log.info("Payment guard evaluation: orderId=$orderId, amount=$amount, valid=$valid")
        return valid
    }
}

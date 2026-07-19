package com.example.statemachine.statemachine.action

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.StateContext
import com.example.statemachine.domain.enums.Market
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.model.Order
import com.example.statemachine.domain.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class PrApprovedAction(
    private val orderRepository: OrderRepository,
) : Action<OrderStatus> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(context: StateContext<OrderStatus>): ActionResult {
        log.info("PrApprovedAction.execute() called")

        val orderNo = context.headers["orderNo"] as? String
        val productId = context.headers["productId"] as? String
        val productName = context.headers["productName"] as? String
        val quantity = (context.headers["quantity"] as? Number)?.toInt()
        val amount = extractAmount(context.headers["amount"])
        val marketStr = context.headers["market"] as? String

        log.info("Headers: orderNo=$orderNo, productId=$productId, quantity=$quantity, amount=$amount, market=$marketStr")

        if (orderNo == null) {
            log.error("Missing required header: orderNo")
            return ActionResult.businessError("Missing required header: orderNo")
        }

        log.info("Processing PR_APPROVED event: orderNo={}", orderNo)

        return try {
            val existingOrder = orderRepository.findByOrderNo(orderNo)

            if (existingOrder != null) {
                log.info("Order already exists: id=${existingOrder.id}, orderNo=$orderNo, status=${existingOrder.status}")
                return ActionResult.success()
            }

            if (marketStr == null) {
                log.error("Missing market header for new order")
                return ActionResult.businessError("Missing market header for new order")
            }

            val market =
                try {
                    Market.valueOf(marketStr)
                } catch (e: IllegalArgumentException) {
                    log.error("Invalid market value: $marketStr")
                    return ActionResult.businessError("Invalid market value: $marketStr")
                }

            val order =
                Order.fromPrApproved(
                    orderNo = orderNo,
                    productId = productId,
                    productName = productName,
                    quantity = quantity,
                    amount = amount,
                    market = market,
                )

            val savedOrder = orderRepository.save(order)
            log.info("Order saved from PR_APPROVED: id=${savedOrder.id}, orderNo=${savedOrder.orderNo}")
            ActionResult.success()
        } catch (e: Exception) {
            log.error("Error in PrApprovedAction: ${e.message}", e)
            ActionResult.technicalError("Error in PrApprovedAction: ${e.message}", e)
        }
    }

    private fun extractAmount(value: Any?): BigDecimal? =
        when (value) {
            is BigDecimal -> value
            is Number -> BigDecimal(value.toString())
            is String -> BigDecimal(value)
            else -> null
        }
}

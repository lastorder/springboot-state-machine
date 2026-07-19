package com.example.statemachine.statemachine.action

import com.example.statemachine.api.Action
import com.example.statemachine.api.ActionResult
import com.example.statemachine.api.StateContext
import com.example.statemachine.domain.enums.Market
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.model.Order
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.infrastructure.persistence.StateMachineJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class PrApprovedAction(
    private val orderRepository: OrderRepository,
    private val stateMachineJpaRepository: StateMachineJpaRepository,
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
        val market =
            marketStr?.let {
                try {
                    Market.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

        log.info("Headers: orderNo=$orderNo, productId=$productId, quantity=$quantity, amount=$amount, market=$market")

        if (orderNo == null) {
            log.error("Missing required header: orderNo")
            return ActionResult.failure("Missing required header: orderNo")
        }

        if (market == null) {
            log.error("Missing or invalid market header: $marketStr")
            return ActionResult.failure("Missing or invalid market header: $marketStr")
        }

        log.info("Processing PR_APPROVED event: orderNo={}", orderNo)

        return try {
            val existingOrder = orderRepository.findByOrderNo(orderNo)

            if (existingOrder != null) {
                val currentStatus = getCurrentState(orderNo)

                log.info("Order already exists: id=${existingOrder.id}, orderNo=$orderNo, currentStatus=$currentStatus")

                if (currentStatus != null && currentStatus != OrderStatus.INIT) {
                    log.info("Order already processed, skipping: orderNo=$orderNo, status=$currentStatus")
                    return ActionResult.success()
                }

                log.info("Re-processed existing order: orderNo=$orderNo")
                return ActionResult.success()
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
            ActionResult.failure("Error in PrApprovedAction: ${e.message}")
        }
    }

    private fun getCurrentState(orderNo: String): OrderStatus? {
        val entity = stateMachineJpaRepository.findById(orderNo).orElse(null)
        return entity?.let { OrderStatus.valueOf(it.state) }
    }

    private fun extractAmount(value: Any?): BigDecimal? =
        when (value) {
            is BigDecimal -> value
            is Number -> BigDecimal(value.toString())
            is String -> BigDecimal(value)
            else -> null
        }
}

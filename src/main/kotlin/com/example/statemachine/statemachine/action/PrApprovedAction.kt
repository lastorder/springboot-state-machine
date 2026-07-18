package com.example.statemachine.statemachine.action

import com.example.statemachine.domain.enums.Market
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
        log.info("PrApprovedAction.execute() called")

        val message = context.message

        val orderNo = message.headers.get("orderNo") as? String
        val productId = message.headers.get("productId") as? String
        val productName = message.headers.get("productName") as? String
        val quantity = (message.headers.get("quantity") as? Number)?.toInt()
        val amount = extractAmount(message.headers.get("amount"))
        val marketStr = message.headers.get("market") as? String
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
            return
        }

        if (market == null) {
            log.error("Missing or invalid market header: $marketStr")
            return
        }

        log.info("Processing PR_APPROVED event: orderNo={}", orderNo)

        try {
            val existingOrder = orderRepository.findByOrderNo(orderNo)

            if (existingOrder != null) {
                val existingStatus = stateMachineService.getCurrentStateByOrderNo(orderNo)

                log.info("Order already exists: id=${existingOrder.id}, orderNo=$orderNo, currentStatus=$existingStatus")

                if (existingStatus != null && existingStatus != OrderStatus.INIT) {
                    log.info("Order already processed, skipping: orderNo=$orderNo, status=$existingStatus")
                    return
                }

                stateMachineService.initializeStateMachineByOrderNo(orderNo, OrderStatus.LOCAL_INITIALIZED)
                log.info("Re-processed existing order: orderNo=$orderNo")
                return
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

            stateMachineService.initializeStateMachineByOrderNo(orderNo, OrderStatus.LOCAL_INITIALIZED)
            log.info("State machine initialized with orderNo: $orderNo")
        } catch (e: Exception) {
            log.error("Error in PrApprovedAction: ${e.message}", e)
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

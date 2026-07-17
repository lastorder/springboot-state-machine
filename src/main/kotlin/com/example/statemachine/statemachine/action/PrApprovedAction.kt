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
        log.info("PrApprovedAction.execute() called")

        val message = context.message

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
            // 检查订单是否已存在
            val existingOrder = orderRepository.findByOrderNo(orderNo)

            if (existingOrder != null) {
                // 订单已存在 - 幂等处理
                val existingStatus = stateMachineService.getCurrentStateByOrderNo(orderNo)

                log.info("Order already exists: id=${existingOrder.id}, orderNo=$orderNo, currentStatus=$existingStatus")

                // 幂等检查：如果订单已经在 LOCAL_INITIALIZED 或之后的状态，直接返回
                if (existingStatus != null && existingStatus != OrderStatus.INIT) {
                    log.info("Order already processed, skipping: orderNo=$orderNo, status=$existingStatus")
                    return
                }

                // 订单存在但仍在 INIT 状态，可能是之前处理失败，重新处理
                stateMachineService.initializeStateMachineByOrderNo(orderNo, OrderStatus.LOCAL_INITIALIZED)
                log.info("Re-processed existing order: orderNo=$orderNo")
                return
            }

            // 创建新订单
            val order =
                Order.fromPrApproved(
                    orderNo = orderNo,
                    productId = productId,
                    productName = productName,
                    quantity = quantity,
                    amount = amount,
                )

            val savedOrder = orderRepository.save(order)
            log.info("Order saved from PR_APPROVED: id=${savedOrder.id}, orderNo=${savedOrder.orderNo}")

            // 使用 orderNo 作为 machineId 初始化状态机
            stateMachineService.initializeStateMachineByOrderNo(orderNo, OrderStatus.LOCAL_INITIALIZED)
            log.info("State machine initialized with orderNo: $orderNo")
        } catch (e: Exception) {
            log.error("Error in PrApprovedAction: ${e.message}", e)
        }
    }
}

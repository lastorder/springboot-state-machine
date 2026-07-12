package com.example.statemachine.service

import com.example.statemachine.controller.dto.CreateOrderRequest
import com.example.statemachine.controller.dto.ModifyOrderRequest
import com.example.statemachine.controller.dto.OrderResponse
import com.example.statemachine.domain.Order
import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val stateMachineService: StateMachineService,
    @Value("\${order.validation.max-retries:3}") private val maxRetries: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        val order =
            Order(
                product = request.product,
                quantity = request.quantity,
                amount = request.amount,
                status = OrderStatus.CREATED,
            )
        val savedOrder = orderRepository.save(order)
        log.info("Created order: id=${savedOrder.id}, product=${savedOrder.product}, quantity=${savedOrder.quantity}")

        // 订单创建后，由 ValidationSubmitAction 在进入 PENDING_VALIDATION 状态时发送验证请求
        // 这里只需要发送 SUBMIT_VALIDATION 事件来触发 Fork
        stateMachineService.sendEvent(savedOrder.id!!, OrderEvent.SUBMIT_VALIDATION)

        return toResponse(savedOrder)
    }

    @Transactional(readOnly = true)
    fun getOrder(id: Long): OrderResponse? {
        return orderRepository.findById(id)
            .map { toResponse(it) }
            .orElse(null)
    }

    @Transactional(readOnly = true)
    fun getOrderEntity(id: Long): Order? {
        return orderRepository.findById(id).orElse(null)
    }

    @Transactional(readOnly = true)
    fun getAllOrders(): List<OrderResponse> {
        return orderRepository.findAll().map { toResponse(it) }
    }

    @Transactional
    fun retryValidation(id: Long): Boolean {
        val order = orderRepository.findById(id).orElse(null) ?: return false

        if (order.status != OrderStatus.PENDING_VALIDATION) {
            log.warn("Cannot retry validation for order in status: ${order.status}")
            return false
        }

        if (order.validationRetryCount >= maxRetries) {
            log.warn("Max retries exceeded for order: $id")
            return false
        }

        order.incrementRetryCount()
        order.resetValidation()
        orderRepository.save(order)

        log.info("Retrying validation for order: id=$id, retryCount=${order.validationRetryCount}")

        stateMachineService.sendEvent(
            orderId = id,
            event = OrderEvent.RETRY_VALIDATION,
        )

        return true
    }

    @Transactional
    fun markInventorySuccess(
        orderId: Long,
        inventoryReference: String?,
    ) {
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        order.markInventorySuccess(inventoryReference)
        orderRepository.save(order)
        log.info("Marked inventory success: orderId=$orderId")
    }

    @Transactional
    fun markInventoryFailed(orderId: Long) {
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        order.markInventoryFailed()
        orderRepository.save(order)
        log.info("Marked inventory failed: orderId=$orderId")
    }

    @Transactional
    fun markPricingSuccess(
        orderId: Long,
        pricingReference: String?,
        unitPrice: BigDecimal?,
    ) {
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        order.markPricingSuccess(pricingReference, unitPrice)
        orderRepository.save(order)
        log.info("Marked pricing success: orderId=$orderId, unitPrice=$unitPrice")
    }

    @Transactional
    fun modifyOrder(
        id: Long,
        request: ModifyOrderRequest,
    ): Boolean {
        if (!request.hasChanges()) {
            log.warn("No changes in modify request for order: $id")
            return false
        }

        val order = orderRepository.findById(id).orElse(null) ?: return false

        if (order.status != OrderStatus.PENDING_CONFIRMATION && order.status != OrderStatus.PENDING_PAYMENT) {
            log.warn("Cannot modify order in status: ${order.status}")
            return false
        }

        order.updateForModification(request.product, request.quantity, "User initiated modification")
        orderRepository.save(order)
        log.info("Order modified: id=$id, newProduct=${request.product}, newQuantity=${request.quantity}")

        stateMachineService.sendEvent(
            orderId = id,
            event = OrderEvent.MODIFY_ORDER,
            headers =
                mapOf(
                    "newProduct" to (request.product ?: order.product),
                    "newQuantity" to (request.quantity ?: order.quantity),
                    "modificationReason" to "User initiated modification",
                ),
        )

        return true
    }

    @Transactional
    fun confirmOrder(id: Long): Boolean {
        val order = orderRepository.findById(id).orElse(null) ?: return false

        if (order.status != OrderStatus.PENDING_CONFIRMATION) {
            log.warn("Cannot confirm order in status: ${order.status}")
            return false
        }

        order.confirmPrice()
        orderRepository.save(order)
        log.info("Order confirmed: id=$id, confirmedPrice=${order.confirmedPrice}")

        stateMachineService.sendEvent(id, OrderEvent.USER_CONFIRM)

        return true
    }

    @Transactional
    fun rejectOrder(
        id: Long,
        reason: String? = null,
    ): Boolean {
        val order = orderRepository.findById(id).orElse(null) ?: return false

        if (order.status != OrderStatus.PENDING_CONFIRMATION) {
            log.warn("Cannot reject order in status: ${order.status}")
            return false
        }

        log.info("Order rejected: id=$id, reason=$reason")

        stateMachineService.sendEvent(id, OrderEvent.USER_REJECT)

        return true
    }

    @Transactional
    fun updateFromInventoryModification(
        orderId: Long,
        modifiedProduct: String?,
        modifiedQuantity: Int?,
        reason: String,
    ) {
        val order = orderRepository.findById(orderId).orElse(null) ?: return
        order.updateForModification(modifiedProduct, modifiedQuantity, reason)
        orderRepository.save(order)
        log.info(
            "Order updated from inventory modification: orderId=$orderId, " +
                "modifiedProduct=$modifiedProduct, modifiedQuantity=$modifiedQuantity, reason=$reason",
        )
    }

    @Transactional
    fun submitOrder(id: Long): Boolean {
        val order = orderRepository.findById(id).orElse(null) ?: return false
        log.info("Submit order called for id=$id, current status=${order.status}")
        return true
    }

    @Transactional
    fun payOrder(
        id: Long,
        amount: BigDecimal,
    ): Boolean {
        val order = orderRepository.findById(id).orElse(null) ?: return false
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            log.warn("Cannot pay order in status: ${order.status}")
            return false
        }

        val success =
            stateMachineService.sendEvent(
                id,
                OrderEvent.PAY,
                mapOf("amount" to amount),
            )
        if (success) {
            order.updateStatus(OrderStatus.PAID)
            orderRepository.save(order)
            log.info("Order paid: id=$id, amount=$amount")
        }
        return success
    }

    @Transactional
    fun updateOrderStatus(
        id: Long,
        newStatus: OrderStatus,
    ): Boolean {
        val order = orderRepository.findById(id).orElse(null) ?: return false
        order.updateStatus(newStatus)
        orderRepository.save(order)
        log.info("Order status updated: id=$id, newStatus=$newStatus")
        return true
    }

    @Transactional
    fun cancelOrder(id: Long): Boolean {
        val order = orderRepository.findById(id).orElse(null) ?: return false

        val canCancel =
            order.status in
                setOf(
                    OrderStatus.CREATED,
                    OrderStatus.PENDING_VALIDATION,
                    OrderStatus.PENDING_CONFIRMATION,
                    OrderStatus.PENDING_PAYMENT,
                    OrderStatus.PENDING_SHIPMENT,
                )

        if (!canCancel) {
            log.warn("Cannot cancel order in status: ${order.status}")
            return false
        }

        val success = stateMachineService.sendEvent(id, OrderEvent.CANCEL)
        if (success) {
            order.updateStatus(OrderStatus.CANCELLED)
            orderRepository.save(order)
            log.info("Order cancelled: id=$id")
        }
        return success
    }

    private fun toResponse(order: Order): OrderResponse {
        return OrderResponse(
            id = order.id!!,
            product = order.product,
            quantity = order.quantity,
            amount = order.amount,
            status = order.status,
            inventoryStatus = order.inventoryStatus?.name,
            inventoryReference = order.inventoryReference,
            pricingReference = order.pricingReference,
            unitPrice = order.unitPrice,
            confirmedPrice = order.confirmedPrice,
            modificationReason = order.modificationReason,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
        )
    }
}

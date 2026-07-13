package com.example.statemachine.order.service

import com.example.statemachine.commandinbox.domain.CommandPriority
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.model.Order
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.presentation.dto.CreateOrderRequest
import com.example.statemachine.presentation.dto.OrderResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderCommandService: OrderCommandService,
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

        orderCommandService.submitOrderEvent(
            orderId = savedOrder.id!!,
            event = OrderEvent.SUBMIT_VALIDATION,
            priority = CommandPriority.HIGH,
        )

        return toResponse(savedOrder)
    }

    @Transactional(readOnly = true)
    fun getOrder(id: Long): OrderResponse? = orderRepository.findById(id)?.let { toResponse(it) }

    @Transactional(readOnly = true)
    fun getOrderEntity(id: Long): Order? = orderRepository.findById(id)

    @Transactional(readOnly = true)
    fun getAllOrders(): List<OrderResponse> = orderRepository.findAll().map { toResponse(it) }

    @Transactional
    fun markInventorySuccess(
        orderId: Long,
        inventoryReference: String?,
    ) {
        val order = orderRepository.findById(orderId) ?: return
        order.markInventorySuccess(inventoryReference)
        orderRepository.save(order)
        log.info("Marked inventory success: orderId=$orderId")
    }

    @Transactional
    fun markInventoryFailed(orderId: Long) {
        val order = orderRepository.findById(orderId) ?: return
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
        val order = orderRepository.findById(orderId) ?: return
        order.markPricingSuccess(pricingReference, unitPrice)
        orderRepository.save(order)
        log.info("Marked pricing success: orderId=$orderId, unitPrice=$unitPrice")
    }

    @Transactional
    fun updateFromInventoryModification(
        orderId: Long,
        modifiedProduct: String?,
        modifiedQuantity: Int?,
        reason: String,
    ) {
        val order = orderRepository.findById(orderId) ?: return
        order.updateForModification(modifiedProduct, modifiedQuantity, reason)
        orderRepository.save(order)
        log.info(
            "Order updated from inventory modification: orderId=$orderId, " +
                "modifiedProduct=$modifiedProduct, modifiedQuantity=$modifiedQuantity, reason=$reason",
        )
    }

    @Transactional
    fun updateOrderStatus(
        id: Long,
        newStatus: OrderStatus,
    ): Boolean {
        val order = orderRepository.findById(id) ?: return false
        order.updateStatus(newStatus)
        orderRepository.save(order)
        log.info("Order status updated: id=$id, newStatus=$newStatus")
        return true
    }

    @Transactional
    fun updateOrderForPayment(
        id: Long,
        amount: BigDecimal,
    ): Boolean {
        val order = orderRepository.findById(id) ?: return false
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            log.warn("Cannot pay order in status: ${order.status}")
            return false
        }
        order.updateStatus(OrderStatus.PAID)
        orderRepository.save(order)
        log.info("Order paid: id=$id, amount=$amount")
        return true
    }

    @Transactional
    fun updateOrderForCancellation(id: Long): Boolean {
        val order = orderRepository.findById(id) ?: return false

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

        order.updateStatus(OrderStatus.CANCELLED)
        orderRepository.save(order)
        log.info("Order cancelled: id=$id")
        return true
    }

    private fun toResponse(order: Order): OrderResponse =
        OrderResponse(
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

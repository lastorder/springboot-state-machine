package com.example.statemachine.service

import com.example.statemachine.controller.dto.CreateOrderRequest
import com.example.statemachine.controller.dto.OrderResponse
import com.example.statemachine.domain.Order
import com.example.statemachine.domain.OrderEvent
import com.example.statemachine.domain.OrderStatus
import com.example.statemachine.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val stateMachineService: StateMachineService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        val order =
            Order(
                product = request.product,
                amount = request.amount,
                status = OrderStatus.CREATED,
            )
        val savedOrder = orderRepository.save(order)
        log.info("Created order: id=${savedOrder.id}, product=${savedOrder.product}, amount=${savedOrder.amount}")
        return toResponse(savedOrder)
    }

    @Transactional(readOnly = true)
    fun getOrder(id: Long): OrderResponse? {
        return orderRepository.findById(id)
            .map { toResponse(it) }
            .orElse(null)
    }

    @Transactional(readOnly = true)
    fun getAllOrders(): List<OrderResponse> {
        return orderRepository.findAll().map { toResponse(it) }
    }

    @Transactional
    fun submitOrder(id: Long): Boolean {
        val order = orderRepository.findById(id).orElse(null) ?: return false
        if (order.status != OrderStatus.CREATED) {
            log.warn("Cannot submit order in status: ${order.status}")
            return false
        }

        val success = stateMachineService.sendEvent(id, OrderEvent.SUBMIT)
        if (success) {
            order.updateStatus(OrderStatus.PENDING_PAYMENT)
            orderRepository.save(order)
            log.info("Order submitted: id=$id")
        }
        return success
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
        if (order.status == OrderStatus.SHIPPED || order.status == OrderStatus.DELIVERED) {
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
            amount = order.amount,
            status = order.status,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
        )
    }
}

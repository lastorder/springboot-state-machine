package com.example.statemachine.order.service

import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.domain.model.Order
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.presentation.dto.CreateOrderRequest
import com.example.statemachine.presentation.dto.OrderResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        val order =
            Order(
                orderNo = request.orderNo,
                productId = request.productId,
                productName = request.productName,
                quantity = request.quantity ?: 1,
                amount = request.amount,
                market = request.market,
            )
        val savedOrder = orderRepository.save(order)
        log.info("Created order: id=${savedOrder.id}, orderNo=${savedOrder.orderNo}")

        return toResponse(savedOrder)
    }

    @Transactional(readOnly = true)
    fun getOrder(id: Long): OrderResponse? = orderRepository.findById(id)?.let { toResponse(it) }

    @Transactional(readOnly = true)
    fun getOrderEntity(id: Long): Order? = orderRepository.findById(id)

    @Transactional(readOnly = true)
    fun getAllOrders(): List<OrderResponse> = orderRepository.findAll().map { toResponse(it) }

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
    fun saveOrder(order: Order): Order = orderRepository.save(order)

    private fun toResponse(order: Order): OrderResponse {
        val id = order.id ?: throw IllegalStateException("Order ID must not be null when converting to response")
        return OrderResponse(
            id = id,
            orderNo = order.orderNo,
            productId = order.productId,
            productName = order.productName,
            quantity = order.quantity,
            amount = order.amount,
            status = order.status,
            market = order.market,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
        )
    }
}

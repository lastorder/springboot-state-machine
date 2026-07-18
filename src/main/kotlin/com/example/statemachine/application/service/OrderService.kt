package com.example.statemachine.application.service

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
    fun getOrderByOrderNo(orderNo: String): OrderResponse? = orderRepository.findByOrderNo(orderNo)?.let { toResponse(it) }

    @Transactional(readOnly = true)
    fun getAllOrders(): List<OrderResponse> = orderRepository.findAll().map { toResponse(it) }

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

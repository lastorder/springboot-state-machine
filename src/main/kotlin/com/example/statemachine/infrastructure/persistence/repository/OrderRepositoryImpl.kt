package com.example.statemachine.infrastructure.persistence.repository

import com.example.statemachine.domain.model.Order
import com.example.statemachine.domain.repository.OrderRepository
import com.example.statemachine.infrastructure.persistence.converter.OrderConverter
import org.springframework.stereotype.Repository

@Repository
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun save(order: Order): Order {
        val entity = OrderConverter.toEntity(order)
        val savedEntity = orderJpaRepository.save(entity)
        return OrderConverter.toDomain(savedEntity)
    }

    override fun findById(id: Long): Order? =
        orderJpaRepository
            .findById(id)
            .map { OrderConverter.toDomain(it) }
            .orElse(null)

    override fun findByOrderNo(orderNo: String): Order? =
        orderJpaRepository
            .findByOrderNo(orderNo)
            ?.let { OrderConverter.toDomain(it) }

    override fun findAll(): List<Order> = orderJpaRepository.findAll().map { OrderConverter.toDomain(it) }
}

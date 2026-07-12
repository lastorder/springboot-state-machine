package com.example.statemachine.domain.repository

import com.example.statemachine.domain.model.Order

interface OrderRepository {
    fun save(order: Order): Order

    fun findById(id: Long): Order?

    fun findAll(): List<Order>
}

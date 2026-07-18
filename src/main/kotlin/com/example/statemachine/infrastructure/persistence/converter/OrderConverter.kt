package com.example.statemachine.infrastructure.persistence.converter

import com.example.statemachine.domain.model.Order
import com.example.statemachine.infrastructure.persistence.entity.OrderJpaEntity

object OrderConverter {
    fun toEntity(order: Order): OrderJpaEntity =
        OrderJpaEntity(
            id = order.id,
            orderNo = order.orderNo,
            productId = order.productId,
            productName = order.productName,
            quantity = order.quantity,
            amount = order.amount,
            status = order.status,
            market = order.market,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
            version = order.version,
        )

    fun toDomain(entity: OrderJpaEntity): Order =
        Order(
            id = entity.id,
            orderNo = entity.orderNo,
            productId = entity.productId,
            productName = entity.productName,
            quantity = entity.quantity,
            amount = entity.amount,
            status = entity.status,
            market = entity.market,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            version = entity.version,
        )
}

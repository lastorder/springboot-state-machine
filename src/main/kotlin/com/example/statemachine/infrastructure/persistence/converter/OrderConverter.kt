package com.example.statemachine.infrastructure.persistence.converter

import com.example.statemachine.domain.model.Order
import com.example.statemachine.infrastructure.persistence.entity.OrderJpaEntity

object OrderConverter {
    fun toEntity(order: Order): OrderJpaEntity =
        OrderJpaEntity(
            id = order.id,
            product = order.product,
            quantity = order.quantity,
            amount = order.amount,
            status = order.status,
            inventoryStatus = order.inventoryStatus,
            inventoryReference = order.inventoryReference,
            inventoryCheckStatus = order.inventoryCheckStatus,
            pricingCheckStatus = order.pricingCheckStatus,
            inventoryCheckedAt = order.inventoryCheckedAt,
            pricingCheckedAt = order.pricingCheckedAt,
            validationStartedAt = order.validationStartedAt,
            validationRetryCount = order.validationRetryCount,
            pricingReference = order.pricingReference,
            unitPrice = order.unitPrice,
            confirmedPrice = order.confirmedPrice,
            modificationReason = order.modificationReason,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt,
            version = order.version,
        )

    fun toDomain(entity: OrderJpaEntity): Order =
        Order(
            id = entity.id,
            product = entity.product,
            quantity = entity.quantity,
            amount = entity.amount,
            status = entity.status,
            inventoryStatus = entity.inventoryStatus,
            inventoryReference = entity.inventoryReference,
            inventoryCheckStatus = entity.inventoryCheckStatus,
            pricingCheckStatus = entity.pricingCheckStatus,
            inventoryCheckedAt = entity.inventoryCheckedAt,
            pricingCheckedAt = entity.pricingCheckedAt,
            validationStartedAt = entity.validationStartedAt,
            validationRetryCount = entity.validationRetryCount,
            pricingReference = entity.pricingReference,
            unitPrice = entity.unitPrice,
            confirmedPrice = entity.confirmedPrice,
            modificationReason = entity.modificationReason,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            version = entity.version,
        )
}

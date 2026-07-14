package com.example.statemachine.domain.model

import com.example.statemachine.domain.enums.OrderStatus
import java.math.BigDecimal
import java.time.Instant

class Order(
    var id: Long? = null,
    var orderNo: String,
    var productId: String? = null,
    var productName: String? = null,
    var quantity: Int = 1,
    var amount: BigDecimal? = null,
    var status: OrderStatus = OrderStatus.INIT,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var version: Long = 0,
) {
    fun updateStatus(newStatus: OrderStatus) {
        this.status = newStatus
        this.updatedAt = Instant.now()
    }

    companion object {
        fun fromPrApproved(
            orderNo: String,
            productId: String?,
            productName: String?,
            quantity: Int?,
            amount: BigDecimal?,
        ): Order =
            Order(
                orderNo = orderNo,
                productId = productId,
                productName = productName,
                quantity = quantity ?: 1,
                amount = amount,
            )
    }
}

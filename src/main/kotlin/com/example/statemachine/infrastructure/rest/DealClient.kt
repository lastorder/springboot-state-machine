package com.example.statemachine.infrastructure.rest

interface DealClient {
    /**
     * 同步订单到 Deal 服务（使用 orderNo）
     */
    fun syncOrderByOrderNo(orderNo: String)

    /**
     * 同步订单到 Deal 服务（使用 orderId）
     * @deprecated Use syncOrderByOrderNo instead
     */
    @Deprecated("Use syncOrderByOrderNo instead")
    fun syncOrder(orderId: Long) {
        // Default implementation for backward compatibility
    }
}

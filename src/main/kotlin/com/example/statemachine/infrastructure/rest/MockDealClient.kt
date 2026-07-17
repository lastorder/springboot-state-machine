package com.example.statemachine.infrastructure.rest

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MockDealClient : DealClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun syncOrderByOrderNo(orderNo: String) {
        log.info("[Mock] Syncing order to deal service: orderNo={}", orderNo)
        // 模拟 REST API 调用
    }

    @Deprecated("Use syncOrderByOrderNo instead", ReplaceWith("syncOrderByOrderNo(orderNo)"))
    override fun syncOrder(orderId: Long) {
        log.info("[Mock] Syncing order to deal service: orderId={}", orderId)
    }
}

package com.example.statemachine.infrastructure.rest

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MockDealClient : DealClient {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun syncOrderByOrderNo(orderNo: String) {
        log.info("[Mock] Syncing order to deal service: orderNo={}", orderNo)
    }
}

package com.example.statemachine.statemachine.service

import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class OrderStatusSyncService(
    private val orderJpaRepository: OrderJpaRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun syncOrderStatusByOrderNo(
        orderNo: String,
        newStatus: OrderStatus,
    ): Boolean {
        if (orderNo.isBlank()) {
            log.warn("Empty orderNo for status sync")
            return false
        }

        return try {
            val updated = orderJpaRepository.updateStatusByOrderNo(orderNo, newStatus)
            if (updated > 0) {
                log.info("Synced order status: orderNo=$orderNo, status=$newStatus")
                true
            } else {
                log.warn("Order not found for status sync: orderNo=$orderNo")
                false
            }
        } catch (e: Exception) {
            log.error("Failed to sync order status: orderNo=$orderNo, status=$newStatus", e)
            false
        }
    }
}

package com.example.statemachine.statemachine.service

import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.persistence.repository.OrderJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 订单状态同步服务
 * 使用独立事务，确保状态同步失败不影响状态机执行
 */
@Service
class OrderStatusSyncService(
    private val orderJpaRepository: OrderJpaRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 通过 orderNo 同步订单状态
     */
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
            val entity =
                orderJpaRepository.findByOrderNo(orderNo)
                    ?: run {
                        log.warn("Order not found for status sync: orderNo=$orderNo")
                        return false
                    }

            entity.status = newStatus
            entity.updatedAt = java.time.Instant.now()
            orderJpaRepository.save(entity)
            log.info("Synced order status: orderNo=$orderNo, status=$newStatus")
            true
        } catch (e: Exception) {
            log.error("Failed to sync order status: orderNo=$orderNo, status=$newStatus", e)
            throw e
        }
    }

    /**
     * 通过 orderId 同步订单状态
     * @deprecated 使用 syncOrderStatusByOrderNo
     */
    @Deprecated("Use syncOrderStatusByOrderNo instead")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun syncOrderStatus(
        orderId: Long,
        newStatus: OrderStatus,
    ): Boolean {
        val updated: Int = orderJpaRepository.updateStatus(orderId, newStatus)
        return if (updated > 0) {
            log.info("Synced order status: orderId=$orderId, status=$newStatus")
            true
        } else {
            log.warn("Order not found for status sync: orderId=$orderId")
            false
        }
    }
}

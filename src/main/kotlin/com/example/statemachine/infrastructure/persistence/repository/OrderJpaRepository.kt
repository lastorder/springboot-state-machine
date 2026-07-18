package com.example.statemachine.infrastructure.persistence.repository

import com.example.statemachine.domain.enums.OrderStatus
import com.example.statemachine.infrastructure.persistence.entity.OrderJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    fun findByOrderNo(orderNo: String): OrderJpaEntity?

    @Modifying
    @Query("UPDATE OrderJpaEntity o SET o.status = :status, o.updatedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    fun updateStatus(
        @Param("id") id: Long,
        @Param("status") status: OrderStatus,
    ): Int

    @Modifying
    @Query(
        "UPDATE OrderJpaEntity o SET o.status = :status, o.updatedAt = CURRENT_TIMESTAMP, o.version = o.version + 1 WHERE o.orderNo = :orderNo",
    )
    fun updateStatusByOrderNo(
        @Param("orderNo") orderNo: String,
        @Param("status") status: OrderStatus,
    ): Int

    @Query("SELECT o.id FROM OrderJpaEntity o WHERE o.orderNo = :orderNo")
    fun findIdByOrderNo(
        @Param("orderNo") orderNo: String,
    ): Long?
}

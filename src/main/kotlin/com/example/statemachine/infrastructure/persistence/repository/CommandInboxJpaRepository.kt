package com.example.statemachine.infrastructure.persistence.repository

import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.domain.enums.OrderEvent
import com.example.statemachine.infrastructure.persistence.entity.CommandInboxJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CommandInboxJpaRepository : JpaRepository<CommandInboxJpaEntity, Long> {
    @Query(
        """
        SELECT c FROM CommandInboxJpaEntity c
        WHERE c.orderId = :orderId
          AND c.status = 'PENDING'
          AND (c.expiresAt IS NULL OR c.expiresAt > :now)
        ORDER BY c.priority DESC, c.id ASC
        LIMIT 1
        """,
    )
    fun findNextPendingCommand(
        @Param("orderId") orderId: Long,
        @Param("now") now: Instant = Instant.now(),
    ): CommandInboxJpaEntity?

    @Query(
        """
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM CommandInboxJpaEntity c
        WHERE c.orderId = :orderId
          AND c.eventType = :eventType
          AND c.idempotencyKey = :idempotencyKey
          AND c.status IN ('PENDING', 'PROCESSING', 'COMPLETED')
        """,
    )
    fun existsByIdempotencyKey(
        @Param("orderId") orderId: Long,
        @Param("eventType") eventType: OrderEvent,
        @Param("idempotencyKey") idempotencyKey: String,
    ): Boolean

    @Query(
        """
        SELECT c FROM CommandInboxJpaEntity c
        WHERE c.status = 'PENDING'
          AND c.expiresAt IS NOT NULL
          AND c.expiresAt < :now
        """,
    )
    fun findExpiredCommands(
        @Param("now") now: Instant,
    ): List<CommandInboxJpaEntity>

    @Query(
        """
        SELECT COUNT(c) FROM CommandInboxJpaEntity c
        WHERE c.orderId = :orderId
          AND c.status = 'PENDING'
          AND (c.expiresAt IS NULL OR c.expiresAt > :now)
        """,
    )
    fun countPendingCommands(
        @Param("orderId") orderId: Long,
        @Param("now") now: Instant = Instant.now(),
    ): Long

    @Query(
        """
        SELECT c FROM CommandInboxJpaEntity c
        WHERE c.id = :id AND c.orderId = :orderId
        """,
    )
    fun findByIdAndOrderId(
        @Param("id") id: Long,
        @Param("orderId") orderId: Long,
    ): CommandInboxJpaEntity?

    @Modifying
    @Query(
        """
        DELETE FROM CommandInboxJpaEntity c
        WHERE c.status = :status
          AND c.processedAt < :cutoffDate
        """,
    )
    fun deleteByStatusAndProcessedAtBefore(
        @Param("status") status: CommandStatus,
        @Param("cutoffDate") cutoffDate: Instant,
    ): Int
}

package com.example.statemachine.infrastructure.persistence.repository

import com.example.statemachine.commandinbox.domain.CommandStatus
import com.example.statemachine.infrastructure.persistence.entity.CommandJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CommandJpaRepository : JpaRepository<CommandJpaEntity, Long> {
    @Query(
        """
        SELECT c FROM CommandJpaEntity c
        WHERE c.groupId = :groupId
          AND c.status = 'PENDING'
        ORDER BY c.priority DESC, c.id ASC
        LIMIT 1
        """,
    )
    fun findNextPendingCommand(
        @Param("groupId") groupId: String,
        @Param("now") now: Instant = Instant.now(),
    ): CommandJpaEntity?

    @Query(
        """
        SELECT c FROM CommandJpaEntity c
        WHERE c.groupId = :groupId
          AND c.status = 'RETRYING'
          AND c.nextRetryAt IS NOT NULL
          AND c.nextRetryAt <= :now
        ORDER BY c.priority DESC, c.id ASC
        LIMIT 1
        """,
    )
    fun findNextRetryableCommand(
        @Param("groupId") groupId: String,
        @Param("now") now: Instant = Instant.now(),
    ): CommandJpaEntity?

    @Query(
        """
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM CommandJpaEntity c
        WHERE c.groupId = :groupId
          AND c.commandType = :commandType
          AND c.idempotencyKey = :idempotencyKey
          AND c.status IN ('PENDING', 'PROCESSING', 'RETRYING', 'COMPLETED')
        """,
    )
    fun existsByIdempotencyKey(
        @Param("groupId") groupId: String,
        @Param("commandType") commandType: String,
        @Param("idempotencyKey") idempotencyKey: String,
    ): Boolean

    @Query(
        """
        SELECT COUNT(c) FROM CommandJpaEntity c
        WHERE c.groupId = :groupId
          AND c.status = 'PENDING'
        """,
    )
    fun countPendingCommands(
        @Param("groupId") groupId: String,
        @Param("now") now: Instant = Instant.now(),
    ): Long

    @Query(
        """
        SELECT c FROM CommandJpaEntity c
        WHERE c.id = :id AND c.groupId = :groupId
        """,
    )
    fun findByIdAndGroupId(
        @Param("id") id: Long,
        @Param("groupId") groupId: String,
    ): CommandJpaEntity?

    @Modifying
    @Query(
        """
        UPDATE CommandJpaEntity c
        SET c.status = :status,
            c.errorMessage = :errorMessage,
            c.response = :response,
            c.processedAt = :processedAt,
            c.nextRetryAt = :nextRetryAt,
            c.updatedAt = CURRENT_TIMESTAMP
        WHERE c.id = :id
        """,
    )
    fun updateStatus(
        @Param("id") id: Long,
        @Param("status") status: CommandStatus,
        @Param("errorMessage") errorMessage: String? = null,
        @Param("response") response: String? = null,
        @Param("processedAt") processedAt: Instant? = null,
        @Param("nextRetryAt") nextRetryAt: Instant? = null,
    ): Int

    @Modifying
    @Query(
        """
        UPDATE CommandJpaEntity c
        SET c.retryCount = c.retryCount + 1,
            c.nextRetryAt = :nextRetryAt,
            c.status = 'RETRYING',
            c.updatedAt = CURRENT_TIMESTAMP
        WHERE c.id = :id
        """,
    )
    fun incrementRetryCount(
        @Param("id") id: Long,
        @Param("nextRetryAt") nextRetryAt: Instant,
    ): Int

    @Modifying
    @Query(
        """
        DELETE FROM CommandJpaEntity c
        WHERE c.status = :status
          AND c.processedAt < :cutoffDate
        """,
    )
    fun deleteByStatusAndProcessedAtBefore(
        @Param("status") status: CommandStatus,
        @Param("cutoffDate") cutoffDate: Instant,
    ): Int
}
